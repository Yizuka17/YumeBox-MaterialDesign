package golang

import (
	// Blank import pulls the cfa native bridge (cfa/native) into this
	// wrapper module so a plain `go build` here type-checks the bridge against
	// the mihomo sources tracked in this directory.
	_ "cfa/native"
)
