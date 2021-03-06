package bbc.cps

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse

import scala.io.Source

object OfflineCompletion {
  implicit val formats = DefaultFormats

  val predicates = Map(
    "MENTIONS" -> "http://www.bbc.co.uk/ontologies/creativework/mentions",
    "EDITORIAL_SENSITIVITY" -> "http://www.bbc.co.uk/ontologies/coreconcepts/editorialSensitivity",
    "ABOUT" -> "http://www.bbc.co.uk/ontologies/passport/predicate/About",
    "EDITORIAL_TONE" -> "http://www.bbc.co.uk/ontologies/coreconcepts/editorialTone",
    "RELEVANT_TO" -> "http://www.bbc.co.uk/ontologies/bbc/relevantTo",
    "EDITORIAL_TONE" -> "http://www.bbc.co.uk/ontologies/coreconcepts/editorialTone",
    "GENRE" -> "http://www.bbc.co.uk/ontologies/bbc/genre",
    "LANGUAGE" -> "http://www.bbc.co.uk/ontologies/coreconcepts/language",
    "FORMAT" -> "http://www.bbc.co.uk/ontologies/cwork/format",
    "CONTRIBUTOR" -> "http://www.bbc.co.uk/ontologies/bbc/contributor",
    "AUDIENCE" -> "audience")

  def passportContainsPredicate(passport: Passport): Map[String, Boolean] =
    predicates.foldLeft(Map.empty[String, Boolean]) { (accum, p) =>
        accum + {
          if (p._1 == "LANGUAGE") {
            p._2 -> passport.language.isDefined
          }
          else {
            passport.taggings match {
              case Some(taggings) => p._2 -> taggings.map(_.predicate).contains(p._2)
              case None => p._2 -> false
            }
          }
        }
    }

  def passportsSummary(filePath: String, domain: String): Map[String, Int] =
    Source.fromFile(filePath).getLines
      .toList
      .map(parse(_).extract[Passport])
      .filter(_.home.contains(domain))
      .zipWithIndex
      .foldLeft(Map.empty[String, Int]) {
        (accum, passport) => {
          accum + ("passports" -> (passport._2 + 1)) ++ // running total from index
            passportContainsPredicate(passport._1).map {
              case (k, v) => k -> ((if (v) 1 else 0) + accum.getOrElse(k, 0))
            }
        }
      }

  def results(summary: Map[String, Int]): Map[String, Double] =
    (summary - "passports").transform((_, v) => v.toDouble / summary("passports").toDouble * 100)

  def tidy(results: Map[String, Double]): Map[String, String] =
    results.map {case (key, value) => predicates.find(_._2 == key).get._1 -> {f"$value%1.1f" + "%"}}

  def main(args: Array[String]): Unit = {
    val (domain,filePath)= (args(0), args(1))
    // Use MAP of predicate counts for domain passports to create MAP of predicate completeness with formatted values
    val completeness = OfflineCompletion.tidy (OfflineCompletion.results (OfflineCompletion.passportsSummary(filePath, domain)))

    // Display formatted table of completeness results.
    if(completeness.isEmpty) {
      println(s"\nNo passports found where passport home = $domain")
    }
    else {
      println(s"\nCompleteness for $domain")
      println(Tabulator.format(List("Predicate", "Completeness") :: completeness.map(x => List(x._1, x._2)).toList))
    }
  }
}