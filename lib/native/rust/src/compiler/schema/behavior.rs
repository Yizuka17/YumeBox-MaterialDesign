//! How each known config field reacts to an override document.
//!
//! `None` means "not part of the known schema" — the patch engine then falls back to generic
//! object/array handling for that key.

use crate::model::{FieldBehavior, ListStyle, SchemaId};

pub fn field_behavior(schema: SchemaId, key: &str) -> Option<FieldBehavior> {
    match schema {
        SchemaId::Root => match key {
            "port"
            | "socks-port"
            | "mixed-port"
            | "redir-port"
            | "allow-lan"
            | "bind-address"
            | "mode"
            | "log-level"
            | "ipv6"
            | "external-controller"
            | "external-controller-tls"
            | "external-doh-server"
            | "secret"
            | "unified-delay"
            | "geodata-mode"
            | "tcp-concurrent"
            | "find-process-mode"
            | "keep-alive-interval"
            | "keep-alive-idle"
            | "interface-name"
            | "routing-mark"
            | "geosite-matcher"
            | "global-client-fingerprint"
            | "geo-auto-update"
            | "geo-update-interval" => Some(FieldBehavior::Scalar),
            "authentication" | "skip-auth-prefixes" | "lan-allowed-ips" | "lan-disallowed-ips" => {
                Some(FieldBehavior::List(ListStyle::Plain))
            }
            "hosts" | "rule-providers" | "proxy-providers" | "sub-rules" => {
                Some(FieldBehavior::Map)
            }
            "proxies" | "proxy-groups" => Some(FieldBehavior::List(ListStyle::NamedObjects)),
            "rules" => Some(FieldBehavior::Rules),
            "dns" => Some(FieldBehavior::Object(SchemaId::Dns)),
            "external-controller-cors" => {
                Some(FieldBehavior::Object(SchemaId::ExternalControllerCors))
            }
            "profile" => Some(FieldBehavior::Object(SchemaId::Profile)),
            "tun" => Some(FieldBehavior::Object(SchemaId::Tun)),
            "sniffer" => Some(FieldBehavior::Object(SchemaId::Sniffer)),
            "geox-url" => Some(FieldBehavior::Object(SchemaId::GeoxUrl)),
            "clash-for-android" => Some(FieldBehavior::Object(SchemaId::App)),
            _ => None,
        },
        SchemaId::Dns => match key {
            "enable"
            | "cache-algorithm"
            | "prefer-h3"
            | "listen"
            | "ipv6"
            | "use-hosts"
            | "use-system-hosts"
            | "respect-rules"
            | "enhanced-mode"
            | "fake-ip-range"
            | "fake-ip-range6"
            | "fake-ip-filter-mode"
            | "fake-ip-ttl"
            | "ipv6-timeout"
            | "cache-max-size"
            | "direct-nameserver-follow-policy" => Some(FieldBehavior::Scalar),
            "nameserver"
            | "fallback"
            | "default-nameserver"
            | "proxy-server-nameserver"
            | "direct-nameserver"
            | "fake-ip-filter" => Some(FieldBehavior::List(ListStyle::Plain)),
            "nameserver-policy" | "proxy-server-nameserver-policy" => Some(FieldBehavior::Map),
            "fallback-filter" => Some(FieldBehavior::Object(SchemaId::DnsFallbackFilter)),
            _ => None,
        },
        SchemaId::DnsFallbackFilter => match key {
            "geoip" | "geoip-code" => Some(FieldBehavior::Scalar),
            "domain" | "ipcidr" | "geosite" => Some(FieldBehavior::List(ListStyle::Plain)),
            _ => None,
        },
        SchemaId::Sniffer => match key {
            "enable" | "force-dns-mapping" | "parse-pure-ip" | "override-destination" => {
                Some(FieldBehavior::Scalar)
            }
            "force-domain" | "skip-domain" | "skip-src-address" | "skip-dst-address" => {
                Some(FieldBehavior::List(ListStyle::Plain))
            }
            "sniff" => Some(FieldBehavior::Object(SchemaId::Sniff)),
            _ => None,
        },
        SchemaId::Sniff => match key {
            "HTTP" | "TLS" | "QUIC" => Some(FieldBehavior::Object(SchemaId::Protocol)),
            _ => None,
        },
        SchemaId::Protocol => match key {
            "ports" => Some(FieldBehavior::List(ListStyle::Plain)),
            "override-destination" => Some(FieldBehavior::Scalar),
            _ => None,
        },
        SchemaId::Tun => match key {
            "enable"
            | "stack"
            | "auto-route"
            | "auto-detect-interface"
            | "auto-redirect"
            | "mtu"
            | "gso"
            | "gso-max-size"
            | "strict-route"
            | "disable-icmp-forwarding"
            | "endpoint-independent-nat" => Some(FieldBehavior::Scalar),
            "dns-hijack"
            | "route-address"
            | "route-exclude-address"
            | "include-package"
            | "exclude-package" => Some(FieldBehavior::List(ListStyle::Plain)),
            _ => None,
        },
        SchemaId::ExternalControllerCors => match key {
            "allow-origins" => Some(FieldBehavior::List(ListStyle::Plain)),
            "allow-private-network" => Some(FieldBehavior::Scalar),
            _ => None,
        },
        SchemaId::Profile => match key {
            "store-selected" | "store-fake-ip" => Some(FieldBehavior::Scalar),
            _ => None,
        },
        SchemaId::GeoxUrl => match key {
            "geoip" | "mmdb" | "geosite" => Some(FieldBehavior::Scalar),
            _ => None,
        },
        SchemaId::App => match key {
            "append-system-dns" => Some(FieldBehavior::Scalar),
            _ => None,
        },
        SchemaId::ProxyItem | SchemaId::ProxyGroupItem | SchemaId::ProviderItem => None,
    }
}
