package main

import (
	"bufio"
	"bytes"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"net"
	"os"
	"strconv"
	"sync"
)

// uid lookup over /proc/net/{tcp,tcp6,udp,udp6}. mihomo's own resolver is unusable here: its
// scanners are unexported and the CMFA build folds the file out of the binary anyway.

// Data-row field indices; all four tables share one layout, so they are read once from tcp.
type procNetColumns struct {
	localAddr int
	uid       int
	inode     int
}

var (
	procNetOnce sync.Once
	procNetCols = procNetColumns{localAddr: -1, uid: -1, inode: -1}
)

func procNetInit() {
	procNetOnce.Do(func() {
		// Often unreadable from the app domain on Android 10+; core.log records which case this is.
		file, err := os.Open("/proc/net/tcp")
		if err != nil {
			fmt.Fprintf(os.Stderr, "procnet: /proc/net/tcp unreadable, uid lookups disabled: %v\n", err)
			return
		}
		defer file.Close()

		scanner := bufio.NewScanner(file)
		if !scanner.Scan() {
			fmt.Fprintln(os.Stderr, "procnet: /proc/net/tcp empty, uid lookups disabled")
			return
		}
		cols := parseProcNetHeader(scanner.Bytes())
		if cols.localAddr < 0 || cols.uid < 0 {
			fmt.Fprintf(os.Stderr, "procnet: unusable header %q, uid lookups disabled\n", scanner.Text())
			return
		}
		procNetCols = cols
	})
}

// The header has more tokens than a data row — "tx_queue rx_queue" and "tr tm->when" each print
// as one colon-joined value — so every collapse before a column shifts its data index one left.
func parseProcNetHeader(header []byte) procNetColumns {
	cols := procNetColumns{localAddr: -1, uid: -1, inode: -1}
	shift := 0
	var sawTxQueue, sawTr bool

	for i, name := range bytes.Fields(header) {
		switch string(name) {
		case "tx_queue":
			sawTxQueue = true
		case "rx_queue":
			if sawTxQueue {
				shift++
			}
		case "tr":
			sawTr = true
		case "tm->when":
			if sawTr {
				shift++
			}
		case "local_address":
			cols.localAddr = i - shift
		case "uid":
			cols.uid = i - shift
		case "inode":
			cols.inode = i - shift
		}
	}
	return cols
}

// The uid owning source's local endpoint, or -1. IPv6 first: dual-stack sockets appear there
// as a v4-mapped address.
func procNetUID(source net.Addr) int {
	procNetInit()
	if procNetCols.localAddr < 0 || procNetCols.uid < 0 {
		return -1
	}

	var (
		ip     net.IP
		port   int
		v4, v6 string
	)
	switch addr := source.(type) {
	case *net.TCPAddr:
		if addr == nil {
			return -1
		}
		ip, port, v4, v6 = addr.IP, addr.Port, "/proc/net/tcp", "/proc/net/tcp6"
	case *net.UDPAddr:
		if addr == nil {
			return -1
		}
		ip, port, v4, v6 = addr.IP, addr.Port, "/proc/net/udp", "/proc/net/udp6"
	default:
		return -1
	}

	var buf [37]byte // 32 hex digits of IPv6 + ':' + 4 of port
	best := -1
	if ip16 := ip.To16(); ip16 != nil {
		uid, exact := scanProcNet(v6, appendProcNetKey(buf[:0], ip16, port))
		if exact {
			return uid
		}
		if uid >= 0 {
			best = uid
		}
	}
	if ip4 := ip.To4(); ip4 != nil {
		uid, exact := scanProcNet(v4, appendProcNetKey(buf[:0], ip4, port))
		if exact {
			return uid
		}
		if uid >= 0 && best < 0 {
			best = uid
		}
	}
	return best
}

// exact = address and port matched in full. A port-only match is returned only when every row
// sharing the port agrees on the uid (unconnected UDP is 00000000:port, so exact never hits, but
// "same port, different apps" is routine here) — better no answer than the wrong app.
func scanProcNet(path string, key []byte) (uid int, exact bool) {
	file, err := os.Open(path)
	if err != nil {
		return -1, false
	}
	defer file.Close()

	portKey := key[len(key)-4:]
	portUID, ambiguous := -1, false

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		row := scanner.Bytes()

		// Port first: four bytes reject nearly every row. Kernel hex is upper, ours lower.
		local := procNetField(row, procNetCols.localAddr)
		if len(local) < 4 || !bytes.EqualFold(portKey, local[len(local)-4:]) {
			continue
		}
		// inode 0 = TIME_WAIT/ownerless, uid reads 0; after port reuse it would shadow the
		// live row and hand the connection to root.
		if procNetCols.inode >= 0 {
			if inode := procNetField(row, procNetCols.inode); len(inode) == 1 && inode[0] == '0' {
				continue
			}
		}
		rowUID, err := strconv.Atoi(string(procNetField(row, procNetCols.uid)))
		if err != nil {
			continue
		}
		if bytes.EqualFold(key, local) {
			return rowUID, true
		}
		switch {
		case portUID < 0:
			portUID = rowUID
		case portUID != rowUID:
			ambiguous = true
		}
	}
	if ambiguous {
		return -1, false
	}
	return portUID, false
}

// "<address>:<port>" as the kernel prints local_address: 4-byte groups in host byte order,
// port big-endian, both hex.
func appendProcNetKey(dst []byte, ip net.IP, port int) []byte {
	var group [4]byte
	for i := 0; i+4 <= len(ip); i += 4 {
		binary.NativeEndian.PutUint32(group[:], binary.BigEndian.Uint32(ip[i:]))
		dst = hex.AppendEncode(dst, group[:])
	}

	dst = append(dst, ':')

	var encoded [2]byte
	binary.BigEndian.PutUint16(encoded[:], uint16(port))
	return hex.AppendEncode(dst, encoded[:])
}

// The idx'th whitespace-separated field, or nil. Unlike bytes.Fields it allocates nothing, and
// it runs against every row.
func procNetField(row []byte, idx int) []byte {
	for {
		for len(row) > 0 && (row[0] == ' ' || row[0] == '\t') {
			row = row[1:]
		}
		if len(row) == 0 {
			return nil
		}

		end := 0
		for end < len(row) && row[end] != ' ' && row[end] != '\t' {
			end++
		}
		if idx == 0 {
			return row[:end]
		}

		idx--
		row = row[end:]
	}
}
