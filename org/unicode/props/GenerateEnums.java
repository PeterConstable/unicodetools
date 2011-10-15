package org.unicode.props;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.cldr.util.RegexUtilities;
import org.unicode.props.PropertyNames.PropertyType;
import org.unicode.text.UCD.Default;
import org.unicode.text.utility.Utility;

import com.ibm.icu.dev.test.util.BagFormatter;

public class GenerateEnums {
    public static final String SOURCE_DIR = "/Users/markdavis/Documents/workspace/DATA/UCD/6.1.0-Update";
    public static final String PROPERTY_FILE_OUTPUT = "/Users/markdavis/Documents/workspace/unicodetools2/org/unicode/props/UcdProperty.java";
    public static final String PROPERTY_VALUE_OUTPUT = "/Users/markdavis/Documents/workspace/unicodetools2/org/unicode/props/PropertyValues.java";

    private static class Locations {
        private static Set<String> files = addAll(new HashSet<String>(), new File(SOURCE_DIR));
        public static boolean contains(String file) {
            return files.contains(file.replace("_",""));
        }
        private static Set<String> addAll(HashSet<String> result, File sourceDir) {
            for (String file : sourceDir.list()) {
                if (!file.endsWith(".txt")) {
                    final File subDir = new File(file);
                    if (subDir.isDirectory()) {
                        addAll(result, subDir);
                    }
                    continue;
                }
                // ArabicShaping-6.1.0d2.txt
                file = file.substring(0,file.length()-4);
                int pos = file.indexOf('-');
                if (pos >= 0) {
                    file = file.substring(0,pos);
                }
                result.add(file);
            }
            return result;
        }
    }


    static Map<String,PropName> lookup = new HashMap<String,PropName>();
    static Map<String,PropName> lookupMain = new TreeMap<String,PropName>();

    private static final Pattern PROPER_LONG_NAME = Pattern.compile("[A-Z]+[0-9]?[a-z]*(_[A-Z0-9]+[a-z]*)*");
    private static final Pattern PROPER_CJK_LONG_NAME = Pattern.compile("(cj)?k[A-Z][a-z]*(_?[A-Z0-9][a-z]*)*");

    static class PropName implements Comparable<PropName>{
        final PropertyType propertyType;
        final String shortName;
        final String longName;
        final List<String> others;
        final Map<String, PropName> subnames = new TreeMap<String, PropName>();
        PropName(PropertyType type, String...strings) {
            propertyType = type;
            shortName = strings[0];
            longName = strings[1];
            if (!isProperLongName(longName, true)) {
                System.out.println("Improper long name: " + longName);
            }
            if (strings.length == 2) {
                others = Collections.emptyList();
            } else {
                List<String> temp = Arrays.asList(strings);
                others = Collections.unmodifiableList(temp.subList(2, strings.length));
            }
            for (String name : strings) {
                if (lookup.containsKey(name)) {
                    throw new IllegalArgumentException("Duplicate propName");
                }
            }
            for (String name : strings) {
                lookup.put(name, this);
            }
            lookupMain.put(longName, this);
        }
        private static boolean isProperLongName(String longName2, boolean allowCjk) {
            boolean result = PROPER_LONG_NAME.matcher(longName2).matches();
            if (result == false && allowCjk) {
                result = PROPER_CJK_LONG_NAME.matcher(longName2).matches();
            }
            if (result == false) {
                System.out.println(RegexUtilities.showMismatch(PROPER_LONG_NAME, longName2));
            }
            return result;
        }
        public String toString() {
            return "{" + propertyType + ",\t" + longName + ",\t" + shortName + ",\t" + others + "}";
        }
        @Override
        public int compareTo(PropName arg0) {
            return longName.compareTo(arg0.longName);
        }
    }
    public static void main(String[] args) throws IOException {

        Map<PropName, List<String[]>> values = new TreeMap<PropName, List<String[]>>();

        addPropertyAliases(values, FileUtilities.in("", Utility.getMostRecentUnicodeDataFile("PropertyAliases", Default.ucdVersion(), true, true)));
        addPropertyAliases(values, FileUtilities.in(GenerateEnums.class, "ExtraPropertyAliases.txt"));

        writeMainUcdFile();

        writeValueEnumFile(values);
    }


    public static void writeValueEnumFile(Map<PropName, List<String[]>> values) throws IOException {
        PrintWriter output = BagFormatter.openUTF8Writer("", PROPERTY_VALUE_OUTPUT);
        output.println("package org.unicode.props;\npublic class PropertyValues {");

        //[Alpha, N, No, F, False]
        addPropertyValueAliases(values, FileUtilities.in("", Utility.getMostRecentUnicodeDataFile("PropertyValueAliases", Default.ucdVersion(), true, true)));
        addPropertyValueAliases(values, FileUtilities.in(GenerateEnums.class, "ExtraPropertyValueAliases.txt"));

        output.println(
                "    public enum Binary {\n"+
                "        No(\"N\", \"F\", \"False\"),\n"+
                "        Yes(\"Y\", \"T\", \"True\");\n"+
                "        private final PropertyNames<Binary> names;\n"+
                "        private Binary (String shortName, String...otherNames) {\n"+
                "            names = new PropertyNames(Binary.class, this, shortName, otherNames);\n"+
                "        }\n"+
                "        public PropertyNames<Binary> getNames() {\n"+
                "            return names;\n"+
                "        }\n"+
                "    }\n"
        );

        for (Entry<PropName, List<String[]>> value : values.entrySet()) {
            final PropName propName = value.getKey();
            System.out.println("Writing:\t" + propName.longName);
            if (propName.propertyType == PropertyType.Binary) {
                continue;
            }
            final List<String[]> partList = value.getValue();
            if (partList.size() == 0) {
                output.println("\t\t// " + propName.longName);
                continue;
            }
            output.println("\tpublic enum " + (propName.longName + "_Values") + " {");
            StringBuilder constants = new StringBuilder();
            boolean first = true;
            for (String[] parts : partList) {
                String longName = parts[2];
                
                // HACK
                if (propName.shortName.equals("ccc")) {
                    longName = parts[3];
                }
                if (!PropName.isProperLongName(longName, false)) {
                    System.out.println("Improper long value name for " + parts[0] + 
                    		": " + longName);
                }
                if (first) {
                    first = false;
                    output.print("        ");
                } else {
                    output.print(",\n        ");
                }
                output.print(fix(longName));
                writeOtherNames2(output, longName, parts);

                for (int i = 1; i < parts.length; ++i) {
                    final String otherName = parts[i];
                    if (i == 2 || otherName.equals("n/a") || otherName.equals(longName) || otherName.contains("-") || otherName.charAt(0) < 'A') {
                        continue;
                    }
                    if (constants.length() != 0) {
                        constants.append(",");
                    }
                    constants.append("\n        " + otherName + "=" + longName);
                }
            }
            String enumName = propName.longName;
            String valuesName = enumName + "_Values.class"; // HACK
            
            output.println(
                    ";\n"  +
                    "        private final PropertyNames<" + enumName + "_Values> names;\n"+
                    "        private " + enumName + "_Values (String shortName, String...otherNames) {\n"+
                    "            names = new PropertyNames(" + valuesName + ", this, shortName, otherNames);\n"+
                    "        }\n"+
                    "        public PropertyNames<" + enumName + "_Values> getNames() {\n"+
                    "            return names;\n"+
                    "        }\n" +
                    "    }\n"
            );
        }
        output.println("\n}");
        output.close();
    }

    // otherNames are x, short, long, others
    // we don't need to do x or long
    public static void writeOtherNames2(PrintWriter output, String longName, String... otherNames) {
        output.print("(");
        boolean haveOne = false;
        for (int i = 1; i < otherNames.length; ++i) {
            String name = otherNames[i];
            if (i != 1) {
                if (longName.equals(name)) {
                    continue;
                }
                output.print(", ");
            }
            if (name.equals("n/a")) {
                output.print("null");
            } else {
                output.print("\"" + name + "\"");
            }
            haveOne = true;
        }
        if (!haveOne) {
            output.print("null");
        }
        output.print(")");
    }


    public static void writeMainUcdFile() throws IOException {
        PrintWriter output = BagFormatter.openUTF8Writer("", PROPERTY_FILE_OUTPUT);

        output.print(
                "//Machine generated: GenerateEnums.java\n" +
                "package org.unicode.props;\n" +
                "import java.util.EnumSet;\n" +
                "import java.util.Set;\n"+
                "import org.unicode.props.PropertyNames.NameMatcher;\n"
        );
        //        "\tpublic enum PropertyType {");
        //        for (PropertyType pt : PropertyType.values()) {
        //            output.print(pt + ", ");
        //        }
        //        output.println("}\n");
        //
        //        output.println(
        //                "\tprivate static <T> void addNames(LinkedHashMap<String, T> map, String[] otherNames, T item) {\n" +
        //                "\t\tmap.put(item.toString(), item);\n" +
        //                "\t\tfor (String other : otherNames) {\n" +
        //                "\t\t\tmap.put(other, item);\n" +
        //                "\t\t}\n" +
        //                "\t}\n"
        //        );
        //
        //
        //        output.println("\tprivate static final LinkedHashMap<String," + "UcdProperty" + "> " + "UcdProperty" + "_Names = new LinkedHashMap<String," + "UcdProperty" + ">();\n");

        output.println("public enum " + "UcdProperty" + " {");
        for (PropertyType pt : PropertyType.values()) {
            int count = 0;
            output.println("\n\t\t// " + pt);
            for (Entry<String, PropName> i : lookupMain.entrySet()) {
                if (i.getValue().propertyType != pt) {
                    continue;
                }
                //                if (count++ > 7) {
                //                    output.println();
                //                    count = 0;
                //                }
                output.print("    " + i.getKey());
                PropName pname = i.getValue();
                String type = "PropertyNames.PropertyType." + pt;
                String classItem = null;
                switch (pt) {
                case Binary:
                    classItem = "PropertyValues.Binary.class";
                    break;
                case Enumerated:
                case Catalog:
                    classItem = "PropertyValues." + ("Script_Extensions".equals(pname.longName) ? "Script" : pname.longName) + "_Values.class"; // HACK!
                    break;
                }
                writeOtherNames(output, type, classItem, pname.longName, pname.shortName);
                output.print(",\n");
            }
        }
        output.println("\t\t;");
        boolean first = true;
        output.println(";\n" +
                "private final PropertyNames.PropertyType type;\n"+
                "\tprivate final PropertyNames<UcdProperty> names;\n"+
                "\t// for enums\n"+
                "\tprivate final NameMatcher name2enum;\n"+
                "\tprivate final EnumSet enums;\n"+
                "\t\n"+
                "\tprivate UcdProperty(PropertyNames.PropertyType type, String shortName, String...otherNames) {\n"+
                "\t\tthis.type = type;\n"+
                "\t\tnames = new PropertyNames(UcdProperty.class, this, shortName, otherNames);\n"+
                "\t\tname2enum = null;\n"+
                "\t\tenums = null;\n"+
                "\t}\n"+
                "\tprivate UcdProperty(PropertyNames.PropertyType type, Class classItem, String shortName, String...otherNames) {\n"+
                "\t\tthis.type = type;\n"+
                "\t\tObject[] x = classItem.getEnumConstants();\n"+
                "\t\tnames = new PropertyNames(UcdProperty.class, this, shortName, otherNames);\n"+
                "\t\tenums = EnumSet.allOf(classItem);\n"+
                "\t\tname2enum = PropertyNames.getNameToEnums(classItem);\n"+
                "\t}\n"+
                "\t\n"+
                "\tpublic PropertyNames.PropertyType getType() {\n"+
                "\t\treturn type;\n"+
                "\t}\n"+
                "\tpublic PropertyNames<UcdProperty> getNames() {\n"+
                "\t\treturn names;\n"+
                "\t}\n"+
                "\tpublic static UcdProperty forString(String name) {\n"+
                "\t\treturn Numeric_Value.names.forString(name);\n"+
                "\t}\n"+
                "\tpublic Enum getEnum(String name) {\n"+
                "\t\treturn name2enum == null ? null : name2enum.get(name);\n"+
                "\t}\n"+
                "\tpublic Set<Enum> getEnums() {\n"+
                "\t\treturn enums;\n"+
                "\t}\n"        
        );

        output.println("\n}");     
        output.close();
    }


    public static void writeOtherNames(PrintWriter output, String type, String classItem, String... otherNames) {
        output.print("(");
        //if (shortName != null) {
        output.print(type);
        if (classItem != null) {
            output.print(", " + classItem);
        }
        boolean first = true;
        for (String otherName : otherNames) {
            if (first) {
                first = false;
                continue;
            }
            output.print(", \"" + otherName + "\"");
        }
        output.print(")");
    }


    public static void addPropertyValueAliases(Map<PropName, List<String[]>> values, Iterable<String> lines) {
        for (String line : lines) {
            String[] parts = FileUtilities.cleanSemiFields(line);
            if (parts == null) {
                continue;
            }
            PropName propName = lookup.get(parts[0]);
            if (propName == null) {
                throw new IllegalArgumentException("Missing Prop Name in " + Arrays.asList(parts));
            }
            List<String[]> set = values.get(propName);
            set.add(parts);
            //System.out.println(propName.longName + "\t" + Arrays.asList(parts));
        }
    }

    public static void addPropertyAliases(Map<PropName, List<String[]>> values, Iterable<String> lines) {
        Matcher propType = Pattern.compile("#\\s+(\\p{Alpha}+)\\s+Properties\\s*").matcher("");
        PropertyType type = null;
        for (String line : lines) {
            //System.out.println(line);
            if (propType.reset(line).matches()) {
                type = PropertyType.valueOf(propType.group(1));
            }
            String[] parts = FileUtilities.cleanSemiFields(line);
            if (parts == null) {
                continue;
            }
            PropName propName = new PropName(type, parts);
            values.put(propName, new ArrayList<String[]>());
            System.out.println(propName);
            //            if (!Locations.contains(propName.longName)) {
            //                System.out.println("Missing file: " + propName.longName);
            //            }
        }
    }
    private static String fix(String string) {
        char ch = string.charAt(0);
        if ('0' <= ch && ch <= '9') {
            return "_" + string.replace('.', '_');
        }
        return string;
    }
}
