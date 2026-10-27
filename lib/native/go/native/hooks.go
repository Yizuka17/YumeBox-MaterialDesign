package main

import (
	"fmt"
	"net"
	"syscall"

	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
)

// ownerQuery resolves a connection to (uid, package), or (-1, "") when unknown; nil in root modes.
type ownerQuery func(protocol int, source, target string) (uid int, pkg string)

// installHooks points mihomo's process-global hooks at this host. vpnHosted marks the VpnService
// child; the root daemon is far less constrained and keeps more of mihomo's own socket path.
func installHooks(sdk int, vpnHosted bool, rpc *launcherRPC) {
	query := socketOwnerQuery(rpc)
	process.DefaultPackageNameResolver = func(metadata *constant.Metadata) (string, error) {
		source := metadata.RawSrcAddr
		if source == nil {
			return "", process.ErrInvalidNetwork
		}

		uid, pkg := socketOwner(sdk, query, source, metadata.RawDstAddr)
		if uid >= 0 {
			metadata.Uid = uint32(uid)
		}

		// Debugln formats and queues before the level check; guard it down to one comparison.
		if log.Level() == log.DEBUG {
			log.Debugln("[PKG] %s --> %s by %d[%s]", metadata.SourceAddress(), metadata.RemoteAddress(), uid, pkg)
		}

		return pkg, nil
	}

	// Left nil for the root daemon: it runs in the root domain and can bind an interface, set a
	// routing mark and use TFO. Tun mode is built on exactly that — TunOverride.kt turns on
	// auto-detect-interface so the core's own egress follows the real default route instead of
	// looping back into the tun — and a non-nil hook is what would suppress it.
	if !vpnHosted {
		return
	}

	// A non-nil hook also makes mihomo skip interfaceName/routingMark binding and TFO, which this
	// process cannot use. The launcher protects only these core sockets, leaving app downloads in
	// the VPN tunnel.
	dialer.DefaultSocketHook = func(_ string, _ string, conn syscall.RawConn) error {
		var protectErr error
		if err := conn.Control(func(fd uintptr) { protectErr = rpc.protect(int(fd)) }); err != nil {
			return err
		}
		if protectErr != nil {
			return fmt.Errorf("protect core socket: %w", protectErr)
		}
		return nil
	}
}

// Launcher RPC first on API 29+ (where /proc/net hides other uids' rows), /proc/net on a miss.
func socketOwner(sdk int, query ownerQuery, source, target net.Addr) (int, string) {
	var protocol int
	// RawSrcAddr is always a *net.TCPAddr / *net.UDPAddr; Network() is never family-suffixed.
	switch source.Network() {
	case "tcp":
		protocol = syscall.IPPROTO_TCP
	case "udp":
		protocol = syscall.IPPROTO_UDP
	default:
		return -1, ""
	}

	// Android 10+ hides /proc/net from the app UID. In VPN mode the launcher query is the only
	// valid owner source; falling back to /proc merely emits a permission error and finds nothing.
	if query != nil && sdk >= 29 {
		if addrUsable(target) {
			if uid, pkg := query(protocol, source.String(), target.String()); uid >= 0 || pkg != "" {
				return uid, pkg
			}
		}
		return -1, ""
	}
	return procNetUID(source), ""
}

// Rejects a typed nil boxed in net.Addr and hostname-only addresses: the launcher would reject
// both, so the IPC round trip would be wasted.
func addrUsable(addr net.Addr) bool {
	switch value := addr.(type) {
	case *net.TCPAddr:
		return value != nil && value.IP != nil
	case *net.UDPAddr:
		return value != nil && value.IP != nil
	default:
		return addr != nil
	}
}
