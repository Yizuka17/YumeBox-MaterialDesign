//! Values the compiler injects when a profile leaves the corresponding block empty.

pub const DEFAULT_NAME_SERVERS: &[&str] = &["223.5.5.5", "119.29.29.29", "8.8.4.4", "1.0.0.1"];
pub const DEFAULT_FAKE_IP_FILTER: &[&str] = &[
    "+.stun.*.*",
    "+.stun.*.*.*",
    "+.stun.*.*.*.*",
    "+.stun.*.*.*.*.*",
    "lens.l.google.com",
    "*.n.n.srv.nintendo.net",
    "+.stun.playstation.net",
    "xbox.*.*.microsoft.com",
    "*.*.xboxlive.com",
    "*.msftncsi.com",
    "*.msftconnecttest.com",
    "*.mcdn.bilivideo.cn",
    "WORKGROUP",
];
pub const DEFAULT_FAKE_IP_RANGE: &str = "28.0.0.0/8";
