//! Identifiers for the known mihomo config schemas and how their fields merge.

#[derive(Clone, Copy, Debug)]
pub enum SchemaId {
    Root,
    Dns,
    DnsFallbackFilter,
    Sniffer,
    Sniff,
    Protocol,
    Tun,
    ExternalControllerCors,
    Profile,
    GeoxUrl,
    App,
    ProxyItem,
    ProxyGroupItem,
    ProviderItem,
}

#[derive(Clone, Copy, Debug)]
pub enum ListStyle {
    Plain,
    NamedObjects,
}

#[derive(Clone, Copy, Debug)]
pub enum FieldBehavior {
    Scalar,
    List(ListStyle),
    Map,
    Object(SchemaId),
    Rules,
}
