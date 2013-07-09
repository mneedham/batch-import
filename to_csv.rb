open("src/main/resources/nodes.csv", 'w') { |f|
  f.puts ["id", "name"].join("\t")
  f.puts ["p1", "Mark"].join("\t")
  f.puts ["p2", "Will"].join("\t")
  f.puts ["p3", "Paul"].join("\t")
}

open("src/main/resources/rels.csv", 'w') { |f|
  f.puts ["start", "end", "type"].join("\t")
  f.puts ["p1","p2","friend_of"].join("\t")
  f.puts ["p2","p1","friend_of"].join("\t")
}