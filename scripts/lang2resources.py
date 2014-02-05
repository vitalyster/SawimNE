#!/usr/bin/env python

import os
import os.path
import codecs
from sys import argv
from sys import exit

import xml.etree.ElementTree as ET

def get_lang(strings_dict):
	return strings_dict["lang"].split(" ")[0].lower().strip()

def lang_lookup(strings_dict, en):
	for item in strings_dict.items():
		if item[1].strip() == en.strip():
			return item[0]

def make_dict(strings):
	return dict(item.split("=") for item in strings)

def indent(elem, level=0):
    i = "\n" + level*"  "
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = i + "  "
        if not elem.tail or not elem.tail.strip():
            elem.tail = i
        for elem in elem:
            indent(elem, level+1)
        if not elem.tail or not elem.tail.strip():
            elem.tail = i
    else:
        if level and (not elem.tail or not elem.tail.strip()):
            elem.tail = i
	
if __name__ == "__main__":	

	strings = filter(lambda line: (not line.startswith("//")) and (len(line.strip()) > 0), map(lambda x: x.strip(), codecs.open(argv[1], "r", "utf-8").readlines()))

	strings_dict = make_dict(strings)

	lang = get_lang(strings_dict)

	if lang == None: 
		print "Not a lang file"
		exit(1)
	lang_strings = ET.Element("resources")

	for key in strings_dict:
		next_string = ET.SubElement(lang_strings, "string", name=key)
		next_string.text = strings_dict[key]
	etree = ET.ElementTree(lang_strings)
	indent(lang_strings)
	ET.dump(lang_strings)


