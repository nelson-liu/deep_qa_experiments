package org.allenai.deep_qa.parse

import org.scalatest._

import org.json4s._
import org.json4s.JsonDSL._

class LogicalFormGeneratorSpec extends FlatSpecLike with Matchers {
  val parser = new StanfordParser
  val logicalFormGenerator = new LogicalFormGenerator(JNothing)
  val nestedParams: JValue = ("nested" -> true)
  val nestedGenerator = new LogicalFormGenerator(nestedParams)

  "getLogicalForm" should "handle basic alternations (intransitive verb)" in {
    val parse = parser.parseSentence("Animals eat.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("eat", Seq(Atom("animal")))
    ))
  }

  it should "handle basic alternations (passive verb with no agent)" in {
    val parse = parser.parseSentence("Animals are eaten.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("eaten", Seq(Atom("animal")))
    ))
  }

  it should "handle basic alternations (passive verb with agent)" in {
    val parse = parser.parseSentence("Animals are eaten by wolves.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("eat", Seq(Atom("wolf"), Atom("animal")))
    ))
  }

  it should "handle basic alternations (passive verb with preposition)" in {
    val parse = parser.parseSentence("Animals are eaten in the city.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("eaten_in", Seq(Atom("animal"), Atom("city")))
    ))
  }

  it should "work for \"Cells contain genetic material called DNA.\" (simplifications)" in {
    val parse = parser.parseSentence("Cells contain genetic material called DNA.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("contain", Seq(Atom("cell"), Atom("genetic material call dna"))),
      Predicate("contain", Seq(Atom("cell"), Atom("genetic material"))),
      Predicate("contain", Seq(Atom("cell"), Atom("material"))),
      Predicate("genetic", Seq(Atom("material"))),
      Predicate("call", Seq(Atom("genetic material"), Atom("dna"))),
      Predicate("call", Seq(Atom("material"), Atom("dna")))
    ))))
  }

  it should "work for \"Most of Earth is covered by water.\" (passives, simplifications)" in {
    val parse = parser.parseSentence("Most of Earth is covered by water.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("cover", Seq(Atom("water"), Atom("most of earth"))),
      Predicate("cover", Seq(Atom("water"), Atom("most"))),
      Predicate("of", Seq(Atom("most"), Atom("earth")))
    ))))
  }

  it should "work for \"Humans depend on plants for oxygen.\" (verbs with prepositions)" in {
    // Unfortunately, the Stanford parser gives an incorrect dependency parse for this sentence, so
    // the logical form is not as we would really want...
    val parse = parser.parseSentence("Humans depend on plants for oxygen.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("depend_on", Seq(Atom("human"), Atom("plant for oxygen"))),
      Predicate("depend_on", Seq(Atom("human"), Atom("plant"))),
      Predicate("for", Seq(Atom("plant"), Atom("oxygen")))
    ))))
  }

  it should "work for \"Humans depend on plants for oxygen.\" (with a correct parse; verbs with two prepositions)" in {
    val tree =
      DependencyTree(Token("depend", "VBP", "depend", 2), Seq(
        (DependencyTree(Token("Humans", "NNS", "human", 1), Seq()), "nsubj"),
        (DependencyTree(Token("plants", "NNS", "plant", 4), Seq()), "prep_on"),
        (DependencyTree(Token("oxygen", "NNS", "oxygen", 6), Seq()), "prep_for")))
    logicalFormGenerator.getLogicalForm(tree) should be(Some(Conjunction(Set(
      Predicate("depend_on", Seq(Atom("human"), Atom("plant"))),
      Predicate("depend_for", Seq(Atom("human"), Atom("oxygen"))),
      Predicate("depend_on_for", Seq(Atom("plant"), Atom("oxygen")))  // preps are sorted by position in sentence
    ))))
  }

  it should "work for \"The seeds of an oak come from the fruit.\" (dropping determiners)" in {
    val parse = parser.parseSentence("The seeds of an oak come from the fruit.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("come_from", Seq(Atom("seed of oak"), Atom("fruit"))),
      Predicate("come_from", Seq(Atom("seed"), Atom("fruit"))),
      Predicate("of", Seq(Atom("seed"), Atom("oak")))
    ))))
  }

  it should "work for \"Which gas is given off by plants?\" (more dropping determiners)" in {
    val parse = parser.parseSentence("Which gas is given off by plants?")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("give_off", Seq(Atom("plant"), Atom("gas")))
    ))
  }

  it should "work for \"MOST erosion at a beach is caused by waves.\" (dropping superlatives)" in {
    val parse = parser.parseSentence("MOST erosion at a beach is caused by waves.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("cause", Seq(Atom("wave"), Atom("erosion at beach"))),
      Predicate("cause", Seq(Atom("wave"), Atom("erosion"))),
      Predicate("at", Seq(Atom("erosion"), Atom("beach")))
    ))))
  }

  it should "work for \"Water causes the most soil and rock erosion.\" (conjunctions)" in {
    // Once again, the Stanford parser is wrong here...
    val parse = parser.parseSentence("Water causes the most soil and rock erosion.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("cause", Seq(Atom("water"), Atom("soil"))),
      Predicate("cause", Seq(Atom("water"), Atom("rock erosion"))),
      Predicate("cause", Seq(Atom("water"), Atom("erosion"))),
      Predicate("rock", Seq(Atom("erosion")))
    ))))
  }

  it should "work for \"Water causes the most soil and rock erosion.\" (with a correct parse; conjunctions)" in {
    val tree =
      DependencyTree(Token("causes", "VBZ", "cause", 2), Seq(
        (DependencyTree(Token("Water", "NN", "water", 1), Seq()), "nsubj"),
        (DependencyTree(Token("erosion", "NN", "erosion", 8), Seq(
          (DependencyTree(Token("the", "DT", "the", 3), Seq()), "det"),
          (DependencyTree(Token("most", "JJS", "most", 4), Seq()), "amod"),
          (DependencyTree(Token("soil", "NN", "soil", 5), Seq(
            (DependencyTree(Token("rock", "NN", "rock", 7), Seq()), "conj_and"))), "nn"),
          (DependencyTree(Token("rock", "NN", "rock", 7), Seq()), "nn"))), "dobj")))
    logicalFormGenerator.getLogicalForm(tree) should be(Some(Conjunction(Set(
      Predicate("cause", Seq(Atom("water"), Atom("soil erosion"))),
      Predicate("cause", Seq(Atom("water"), Atom("rock erosion"))),
      Predicate("cause", Seq(Atom("water"), Atom("erosion"))),
      Predicate("soil", Seq(Atom("erosion"))),
      Predicate("rock", Seq(Atom("erosion")))
    ))))
  }

  it should "work for \"Roots can slow down erosion.\" (dropping modals)" in {
    val parse = parser.parseSentence("Roots can slow down erosion.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("slow_down", Seq(Atom("root"), Atom("erosion")))
    ))
  }

  it should "work for \"regulatory mechanisms of photosynthesis.\"" in {
    val parse = parser.parseSentence("regulatory mechanisms of photosynthesis.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("regulatory", Seq(Atom("mechanism"))),
      Predicate("of", Seq(Atom("regulatory mechanism"), Atom("photosynthesis"))),
      Predicate("of", Seq(Atom("mechanism"), Atom("photosynthesis")))
    ))))
  }

  ignore should "work for \"an example is photosynthesis.\" (copula)" in {
    val parse = parser.parseSentence("an example is photosynthesis.")
    // It would be better to do `example(photosynthesis)` here, but we can't really figure that
    // out, so we have to rely on what the dependency parse gives us for this.
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("photosynthesis", Seq(Atom("example")))
    ))
  }

  it should "work for \"Manganese is necessary for photosynthesis.\" (X BE Y PREP Z)" in {
    val parse = parser.parseSentence("Manganese is necessary for photosynthesis.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("necessary_for", Seq(Atom("manganese"), Atom("photosynthesis")))
    ))
  }

  it should "work for \"Oxygen and energy are products of photosynthesis.\" (conjunctions with X BE Y PREP Z)" in {
    val parse = parser.parseSentence("Oxygen and energy are products of photosynthesis.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("product_of", Seq(Atom("oxygen"), Atom("photosynthesis"))),
      Predicate("product_of", Seq(Atom("energy"), Atom("photosynthesis")))
    ))))
  }

  ignore should "work for \"But, photosynthesis is older.\" (drop CCs)" in {
    val parse = parser.parseSentence("But, photosynthesis is older.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("older", Seq(Atom("photosynthesis")))
    ))
  }

  ignore should "work for \"This process, photosynthesis, liberates oxygen.\" (appositives)" in {
    val parse = parser.parseSentence("This process, photosynthesis, liberates oxygen.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("liberate", Seq(Atom("photosynthesis"), Atom("oxygen"))),
      Predicate("liberate", Seq(Atom("process"), Atom("oxygen"))),
      Predicate("process", Seq(Atom("photosynthesis")))
    ))))
  }

  it should "work for \"Plants generate oxygen during photosynthesis.\" (verb with subj, obj, and prep)" in {
    val parse = parser.parseSentence("Plants generate oxygen during photosynthesis.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("generate", Seq(Atom("plant"), Atom("oxygen"))),
      Predicate("generate_during", Seq(Atom("plant"), Atom("photosynthesis"))),
      Predicate("generate_obj_during", Seq(Atom("oxygen"), Atom("photosynthesis")))
    ))))
  }

  ignore should "work for \"An example would be photosynthesis.\" (copula with modal)" in {
    val parse = parser.parseSentence("An example would be photosynthesis.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("photosynthesis", Seq(Atom("example")))
    ))
  }

  it should "work for \"Photosynthesis is impossible without light.\" (X BE Y PREP Z)" in {
    val parse = parser.parseSentence("Photosynthesis is impossible without light.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("impossible_without", Seq(Atom("photosynthesis"), Atom("light")))
    ))
  }

  it should "work for \"Food originates with photosynthesis .\" (lowercase NNPs)" in {
    // Stanford says "Food" is an NNP in this sentence.
    val parse = parser.parseSentence("Food originates with photosynthesis .")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("originate_with", Seq(Atom("food"), Atom("photosynthesis")))
    ))
  }

  it should "work for \"There are two stages of photosynthesis.\" (existentials)" in {
    val parse = parser.parseSentence("There are two stages of photosynthesis.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("exists", Seq(Atom("two stage of photosynthesis"))),
      Predicate("exists", Seq(Atom("stage of photosynthesis"))),
      Predicate("exists", Seq(Atom("stage"))),
      Predicate("two", Seq(Atom("stage"))),
      Predicate("of", Seq(Atom("two stage"), Atom("photosynthesis"))),
      Predicate("of", Seq(Atom("stage"), Atom("photosynthesis")))
    ))))
  }

  it should "work for \"Photosynthesis gives a plant energy.\" (verb with subj, obj, and obj2)" in {
    val parse = parser.parseSentence("Photosynthesis gives a plant energy.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("give", Seq(Atom("photosynthesis"), Atom("energy"))),
      Predicate("give_obj2", Seq(Atom("photosynthesis"), Atom("plant"))),
      Predicate("give_obj_obj2", Seq(Atom("energy"), Atom("plant")))
    ))))
  }

  it should "work for \"Diuron works by inhibiting photosynthesis.\" (dependent clause with preposition)" in {
    val parse = parser.parseSentence("Diuron works by inhibiting photosynthesis.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("work_by_inhibit", Seq(Atom("diuron"), Atom("photosynthesis")))
    ))
  }

  it should "work for \"Diuron works to inhibit photosynthesis.\" (controlled subject)" in {
    val parse = parser.parseSentence("Diuron works to inhibit photosynthesis.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("work_Csubj_inhibit_obj", Seq(Atom("diuron"), Atom("photosynthesis")))
    ))
  }

  it should "work for \"Sam uses tools to measure things.\" (controlled subject with arguments)" in {
    val parse = parser.parseSentence("Sam uses tools to measure things.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("use_Csubj_measure", Seq(Atom("sam"), Atom("tool"))),
      Predicate("use_Csubj_measure_obj", Seq(Atom("sam"), Atom("thing"))),
      Predicate("use_Csubj_measure_obj_obj", Seq(Atom("tool"), Atom("thing")))
    ))))
  }

  it should "work for \"Tools are used to measure things.\" (passive controlled subject with arguments)" in {
    val parse = parser.parseSentence("Tools are used to measure things.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("use_Csubj_measure_obj_obj", Seq(Atom("tool"), Atom("thing")))
    ))
  }

  it should "work for \"Sue asked Bill to stop.\" (controlled object)" in {
    val parse = parser.parseSentence("Sue asked Bill to stop.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("ask_Cobj_stop", Seq(Atom("sue"), Atom("bill")))
    ))
  }

  it should "work for \"Sue asked Bill to stop the car.\" (controlled object with arguments)" in {
    val parse = parser.parseSentence("Sue asked Bill to stop the car.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("ask_Cobj_stop", Seq(Atom("sue"), Atom("bill"))),
      Predicate("ask_Cobj_stop_obj", Seq(Atom("sue"), Atom("car"))),
      Predicate("ask_Cobj_stop_obj_obj", Seq(Atom("bill"), Atom("car")))
    ))))
  }

  it should "work for \"The plant produces clusters which contain sperm.\" (relative clause with which)" in {
    val parse = parser.parseSentence("The plant produces clusters which contain sperm.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("produce", Seq(Atom("plant"), Atom("cluster which contain sperm"))),
      Predicate("produce", Seq(Atom("plant"), Atom("cluster"))),
      Predicate("contain", Seq(Atom("cluster"), Atom("sperm")))
    ))))
  }

  it should "work for \"There are many ants that eat food.\" (relative clause with that)" in {
    val parse = parser.parseSentence("There are many ants that eat food.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("exists", Seq(Atom("many ant that eat food"))),
      Predicate("exists", Seq(Atom("many ant"))),
      Predicate("many", Seq(Atom("ant"))),
      Predicate("exists", Seq(Atom("ant"))),
      Predicate("eat", Seq(Atom("many ant"), Atom("food"))),
      Predicate("eat", Seq(Atom("ant"), Atom("food")))
    ))))
  }

  it should "work for \"Plant growth in turn sustains animal life.\" (simplification order)" in {
    val parse = parser.parseSentence("Plant growth in turn sustains animal life.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("sustain", Seq(Atom("plant growth in turn"), Atom("animal life"))),
      Predicate("sustain", Seq(Atom("plant growth"), Atom("animal life"))),
      Predicate("sustain", Seq(Atom("growth"), Atom("animal life"))),
      Predicate("sustain", Seq(Atom("plant growth in turn"), Atom("life"))),
      Predicate("sustain", Seq(Atom("plant growth"), Atom("life"))),
      Predicate("sustain", Seq(Atom("growth"), Atom("life"))),
      Predicate("in", Seq(Atom("plant growth"), Atom("turn"))),
      Predicate("in", Seq(Atom("growth"), Atom("turn"))),
      Predicate("plant", Seq(Atom("growth"))),
      Predicate("animal", Seq(Atom("life")))
    ))))
  }

  it should "work for \"A spam from LOGIC@imneverwrong.com, a particularly blatant crank.\" (weird error)" in {
    val parse = parser.parseSentence("A spam from LOGIC@imneverwrong.com, a particularly blatant crank.")
    logicalFormGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(Conjunction(Set(
      Predicate("crank", Seq(Atom("spam"))),
      Predicate("crank", Seq(Atom("spam from logic@imneverwrong.com, particularly blatant"))),
      Predicate("from", Seq(Atom("spam"), Atom("logic@imneverwrong.com, particularly blatant"))),
      Predicate("from", Seq(Atom("spam"), Atom("logic@imneverwrong.com,"))),
      Predicate("blatant", Seq(Atom("logic@imneverwrong.com,")))
    ))))
  }

  it should "properly nest \"Green plants produce oxygen.\" (simple case)" in {
    val parse = parser.parseSentence("Green plants produce oxygen.")
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("produce", Seq(Predicate("green", Seq(Atom("plant"))), Atom("oxygen")))))
  }

  it should "properly nest \"Cells contain genetic material called dna.\" (triple nesting)" in {
    val parse = parser.parseSentence("Cells contain genetic material called dna.")
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("contain", Seq(Atom("cell"), Predicate("call", Seq(Predicate("genetic", Seq(Atom("material"))), Atom("dna")))))))
  }

  ignore should "properly nest \"Humans depend on plants for oxygen.\" (two prepositions) (Stanford gets this wrong)" in {
    val parse = parser.parseSentence("Humans depend on plants for oxygen.")
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("for", Seq(Predicate("depend_on", Seq(Atom("human"), Atom("plant"))), Atom("oxygen")))))
  }

  it should "properly nest \"Humans depend on plants for oxygen.\" (two prepositions) (with a correct parse)" in {
    val tree =
      DependencyTree(Token("depend", "VBP", "depend", 2), Seq(
        (DependencyTree(Token("Humans", "NNS", "human", 1), Seq()), "nsubj"),
        (DependencyTree(Token("plants", "NNS", "plant", 4), Seq()), "prep_on"),
        (DependencyTree(Token("oxygen", "NNS", "oxygen", 6), Seq()), "prep_for")))
    nestedGenerator.getLogicalForm(tree) should be(Some(
      Predicate("for", Seq(Predicate("depend_on", Seq(Atom("human"), Atom("plant"))), Atom("oxygen")))))
  }

  it should "properly nest \"Oxygen is given off by plants.\" (Passive with preposition)" in {
    val parse = parser.parseSentence("Oxygen is given off by plants.")
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("give_off", Seq(Atom("plant"), Atom("oxygen")))))
  }

  it should "properly nest \"The male part of a flower is called stamen.\" (Main verb \"is\")" in {
    val parse = parser.parseSentence("The male part of a flower is called stamen.")
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("called", Seq(Predicate("of", Seq(Predicate("male", Seq(Atom("part"))), Atom("flower"))), Atom("stamen")))))
  }

  it should "properly nest \"Matter that is vibrating is producing sound.\" (Relative clause with that)" in {
    val parse = parser.parseSentence("Matter that is vibrating is producing sound.")
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("produce", Seq(Predicate("vibrate", Seq(Atom("matter"))), Atom("sound")))))
  }

  it should "properly nest \"One example of a consumer is a reindeer.\" (\"example of\" sentence with non-determiner to be ignored)" in {
    val parse = parser.parseSentence("One example of a consumer is a reindeer.")
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("example_of", Seq(Atom("reindeer"), Atom("consumer")))))
  }

  it should "properly nest \"Most of Earth's water is located in oceans.\" (noun-noun relation with \"'s\")" in {
    val parse = parser.parseSentence("Most of Earth's water is located in oceans.")
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("located_in", Seq(Predicate("of", Seq(Atom("most"), Predicate("'s", Seq(Atom("earth"), Atom("water"))))), Atom("ocean")))))
  }

  it should "properly nest \"All known living things are made up of cells.\" (Passive with phrasal verb)" in {
    val parse = parser.parseSentence("All known living things are made up of cells.")
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      // The "all" is missing here because we drop determiners.  It really should be handled as a
      // quantification, anyway...
      Predicate("made_up_of", Seq(Predicate("known", Seq(Predicate("living", Seq(Atom("thing"))))), Atom("cell")))))
  }

  ignore should "properly nest \"Oxygen and energy are products of photosynthesis.\" (conjunctions with X BE Y PREP Z)" in {
    val parse = parser.parseSentence("Oxygen and energy are products of photosynthesis.")
    parse.dependencyTree.get.print()
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("product_of", Seq(Conjunction(Set(Atom("oxygen"), Atom("energy"))), Atom("photosynthesis")))
    ))
  }

  ignore should "properly nest \"Water causes the most soil and rock erosion.\" (with a correct parse; conjunctions)" in {
    val tree =
      DependencyTree(Token("causes", "VBZ", "cause", 2), Seq(
        (DependencyTree(Token("Water", "NN", "water", 1), Seq()), "nsubj"),
        (DependencyTree(Token("erosion", "NN", "erosion", 8), Seq(
          (DependencyTree(Token("the", "DT", "the", 3), Seq()), "det"),
          (DependencyTree(Token("most", "JJS", "most", 4), Seq()), "amod"),
          (DependencyTree(Token("soil", "NN", "soil", 5), Seq(
            (DependencyTree(Token("rock", "NN", "rock", 7), Seq()), "conj_and"))), "nn"),
          (DependencyTree(Token("rock", "NN", "rock", 7), Seq()), "nn"))), "dobj")))
    tree.print()
    nestedGenerator.getLogicalForm(tree) should be(Some(
      Predicate("cause", Seq(
        Atom("water"),
        Conjunction(Set(Predicate("soil", Seq(Atom("erosion"))), Predicate("rock", Seq(Atom("erosion")))))))
    ))
  }

  ignore should "work with nested predicates for \"The end stage in a human's life cycle is death.\" (preposition as a predicate)" in {
    val parse = parser.parseSentence("The end stage in a human's life cycle is death.")
    parse.dependencyTree.get.print()
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("be", Seq(
        Predicate("in", Seq(
          Predicate("end", Seq(Atom("stage"))),
          Predicate("'s", Seq(Atom("human"), Predicate("life", Seq(Atom("cycle"))))))),
        Atom("death"))
      )))
  }

  ignore should "work with nested predicates for \"One thing all organisms need to survive is energy.\" (Question to statement)" in {
    val parse = parser.parseSentence("One thing all organisms need to survive is energy.")
    parse.dependencyTree.get.print()
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("be", Seq(Predicate("need_Csubj_survive", Seq(Atom("organism"), Predicate("one", Seq(Atom("thing"))))), Atom("energy")))
      ))
  }

  ignore should "work with nested predicates for \"A cactus is most likely to compete for water.\" (quantifier)" in {
    val parse = parser.parseSentence("A cactus is most likely to compete for water.")
    parse.dependencyTree.get.print()
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("compete_for", Seq(Atom("cactus"), Atom("water")))
    ))
  }

  it should "not get into an infinite loop with long strings of symbols" in {
    val parse = parser.parseSentence("Walt ======================================== | Cave ne ante ullas catapultas ambules.")
    nestedGenerator.getLogicalForm(parse.dependencyTree.get).toString should not be(None)
  }

  ignore should "handle simple copulatives as unary predicates" in {
    val parse = parser.parseSentence("Pixar is a company.")
    parse.dependencyTree.get.print()
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("company", Seq(Atom("pixar")))
    ))
  }

  ignore should "handle copulatives with a reduced relative" in {
    val parse = parser.parseSentence("Pixar is a company located in California.")
    parse.dependencyTree.get.print()
    nestedGenerator.getLogicalForm(parse.dependencyTree.get) should be(Some(
      Predicate("located_in", Seq(Predicate("company", Seq(Atom("pixar")))))
    ))
  }
}
