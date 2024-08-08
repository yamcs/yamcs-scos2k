from xml.etree import ElementTree as ET

tree = ET.ElementTree()
tree.parse("../pom.xml")
version_el = tree.getroot().find("{http://maven.apache.org/POM/4.0.0}version")

project = "yamcs-scos2k"
copyright = "2024-present, Space Applications Services"
author = "Yamcs Team"
version = version_el.text
release = version
language = "en"
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]
pygments_style = "sphinx"

extensions = [
    "sphinx.ext.extlinks",
    "sphinxcontrib.fulltoc",
    "sphinxcontrib.yamcs",
]

extlinks = {
    "yamcs-manual": ("https://docs.yamcs.org/yamcs-server-manual/%s", None),
}

# Force-disable conversion of -- to en-dash
smartquotes = False

latex_elements = {
    "papersize": "a4paper",
    "figure_align": "htbp",
    "extraclassoptions": "openany",
}

latex_documents = [
    (
        "index",
        "yamcs-scos2k.tex",
        "Yamcs: SCOS2K plugin",
        "Space Applications Services",
        "manual",
    ),
]

latex_show_urls = "footnote"

