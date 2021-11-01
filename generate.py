with open("generate.java", "r") as f:
    with open("generated.txt", "w") as f2:
        f2.write("file.write(" + " + ".join(["\""+ i.replace("\\", "\\\\").replace("\t", "\\t").replace("\"", "\\\"") + "\\n\"" for i in f.read().split("\n")]).replace("\"//Podmien na package\\n\"", "packageName").replace("\"\\t\\t//Podmianka na stringBuilder\\n\"", "stringBuilder") + ");\n")