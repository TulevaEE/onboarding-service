#!/usr/bin/env python3

import os
import re
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

WSDL_URL = "https://ariregxmlv6.rik.ee/?wsdl"
DOCS_URL = "https://avaandmed.ariregister.rik.ee/en/open-data-api/introduction-api-services"
XSD_URL_PATTERN = "https://www2.rik.ee/schemas/xtee6/arireg/live/xroad6_{operation}.xsd"

NS_XSD = "http://www.w3.org/2001/XMLSchema"
NS_WSDL = "http://schemas.xmlsoap.org/wsdl/"
NS_AR = "http://arireg.x-road.eu/producer/"
NS_XRD = "http://x-road.eu/xsd/xroad.xsd"

CACHE_TTL = 3600


def cache_dir():
    for d in [os.environ.get("TMPDIR"), "/tmp"]:
        if d:
            try:
                os.makedirs(d, exist_ok=True)
                return d
            except OSError:
                continue
    return None


def read_cache(name):
    d = cache_dir()
    if not d:
        return None
    path = os.path.join(d, name)
    try:
        if os.path.exists(path) and (time.time() - os.path.getmtime(path)) < CACHE_TTL:
            with open(path, "r", encoding="utf-8") as f:
                return f.read()
    except OSError:
        pass
    return None


def write_cache(name, content):
    d = cache_dir()
    if not d:
        return
    try:
        with open(os.path.join(d, name), "w", encoding="utf-8") as f:
            f.write(content)
    except OSError:
        pass


def fetch_url(url):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "ariregister-skill/1.0"})
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.read().decode("utf-8")
    except Exception as e:
        print(f"Error fetching {url}: {e}", file=sys.stderr)
        sys.exit(1)


def fetch_wsdl():
    cached = read_cache("ariregister_wsdl.xml")
    if cached:
        return cached
    content = fetch_url(WSDL_URL)
    write_cache("ariregister_wsdl.xml", content)
    return content


def fetch_xsd(operation_name):
    cache_name = f"ariregister_xsd_{operation_name}.xml"
    cached = read_cache(cache_name)
    if cached:
        return cached
    url = XSD_URL_PATTERN.format(operation=operation_name)
    content = fetch_url(url)
    write_cache(cache_name, content)
    return content


def parse_wsdl_operations(wsdl_xml):
    root = ET.fromstring(wsdl_xml)

    xsd_urls = {}
    types_section = root.find(f"{{{NS_WSDL}}}types")
    if types_section is not None:
        for schema in types_section.iter(f"{{{NS_XSD}}}schema"):
            includes = list(schema.iter(f"{{{NS_XSD}}}include"))
            for include in includes:
                schema_location = include.get("schemaLocation", "")
                if schema_location:
                    match = re.search(r"xroad6_(.+?)\.xsd", schema_location)
                    if match:
                        op_name = match.group(1)
                        xsd_urls[op_name] = schema_location

    descriptions = {}
    port_type = root.find(f"{{{NS_WSDL}}}portType")
    if port_type is not None:
        for operation in port_type.findall(f"{{{NS_WSDL}}}operation"):
            op_name = operation.get("name", "")
            documentation = operation.find(f"{{{NS_WSDL}}}documentation")
            if documentation is not None:
                title = documentation.find(f"{{{NS_XRD}}}title")
                if title is not None and title.text:
                    descriptions[op_name] = title.text.strip()

    operations = []
    for op_name, xsd_url in xsd_urls.items():
        operations.append(
            {
                "name": op_name,
                "description": descriptions.get(op_name, ""),
                "xsd_url": xsd_url,
            }
        )

    operations.sort(key=lambda x: x["name"])
    return operations


def cmd_wsdl():
    print(fetch_wsdl())


def cmd_docs():
    print(fetch_url(DOCS_URL))


def cmd_list():
    wsdl_xml = fetch_wsdl()
    operations = parse_wsdl_operations(wsdl_xml)

    print("Ariregister API Operations (from WSDL)")
    print("=" * 39)
    print()

    name_width = max((len(op["name"]) for op in operations), default=20) + 2
    for op in operations:
        print(f"{op['name']:<{name_width}} {op['description']}")

    print()
    print(f"Total: {len(operations)} operations")


def get_xrd_title(element):
    annotation = element.find(f"{{{NS_XSD}}}annotation")
    if annotation is None:
        return ""
    appinfo = annotation.find(f"{{{NS_XSD}}}appinfo")
    if appinfo is None:
        return ""
    title = appinfo.find(f"{{{NS_XRD}}}title")
    if title is not None and title.text:
        return title.text.strip()
    return ""


def format_optionality(element):
    min_occurs = element.get("minOccurs", "1")
    max_occurs = element.get("maxOccurs", "1")
    if max_occurs == "unbounded":
        return f"{min_occurs}..*"
    if min_occurs == "0" and max_occurs == "1":
        return "optional"
    if min_occurs == "1" and max_occurs == "1":
        return "required"
    return f"{min_occurs}..{max_occurs}"


def format_type(type_attr):
    if type_attr is None:
        return ""
    if type_attr.startswith("ar:"):
        return type_attr
    if ":" not in type_attr:
        return f"ar:{type_attr}"
    return type_attr


def find_xsd_url_for_operation(operations, operation_name):
    for op in operations:
        if op["name"] == operation_name:
            return op["xsd_url"]
    for op in operations:
        if operation_name.lower() in op["xsd_url"].lower():
            return op["xsd_url"]
    return None


def cmd_describe(operation_name):
    wsdl_xml = fetch_wsdl()
    operations = parse_wsdl_operations(wsdl_xml)

    xsd_url = find_xsd_url_for_operation(operations, operation_name)
    if not xsd_url:
        print(f"Error: operation '{operation_name}' not found in WSDL", file=sys.stderr)
        print("Use 'list' subcommand to see available operations", file=sys.stderr)
        sys.exit(1)

    xsd_xml = fetch_xsd(operation_name)
    root = ET.fromstring(xsd_xml)

    print(f"Operation: {operation_name}")
    print(f"Schema: {xsd_url}")

    complex_types = root.findall(f"{{{NS_XSD}}}complexType")
    for complex_type in complex_types:
        type_name = complex_type.get("name", "")
        print()
        print(f"Type: {type_name}")

        sequence = complex_type.find(f".//{{{NS_XSD}}}sequence")
        if sequence is None:
            continue

        elements = sequence.findall(f"{{{NS_XSD}}}element")
        if not elements:
            continue

        name_width = max((len(el.get("name", "")) for el in elements), default=10) + 2
        type_width = max(
            (len(format_type(el.get("type"))) for el in elements if el.get("type")), default=15
        ) + 2

        for element in elements:
            el_name = element.get("name", "")
            el_type = format_type(element.get("type"))
            optionality = format_optionality(element)
            annotation = get_xrd_title(element)
            print(f"  {el_name:<{name_width}} {el_type:<{type_width}} {optionality:<10} {annotation}".rstrip())

    top_level_elements = root.findall(f"{{{NS_XSD}}}element")
    if top_level_elements:
        print()
        print("Elements:")
        name_width = max((len(el.get("name", "")) for el in top_level_elements), default=10) + 2
        for element in top_level_elements:
            el_name = element.get("name", "")
            el_type = format_type(element.get("type"))
            print(f"  {el_name:<{name_width}} type: {el_type}")


def main():
    args = sys.argv[1:]
    if not args:
        print("Usage: ariregister.py <subcommand> [args]", file=sys.stderr)
        print("Subcommands: wsdl, docs, list, describe <operation_name>", file=sys.stderr)
        sys.exit(1)

    subcommand = args[0]

    if subcommand == "wsdl":
        cmd_wsdl()
    elif subcommand == "docs":
        cmd_docs()
    elif subcommand == "list":
        cmd_list()
    elif subcommand == "describe":
        if len(args) < 2:
            print("Usage: ariregister.py describe <operation_name>", file=sys.stderr)
            sys.exit(1)
        cmd_describe(args[1])
    else:
        print(f"Unknown subcommand: {subcommand}", file=sys.stderr)
        print("Subcommands: wsdl, docs, list, describe <operation_name>", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
