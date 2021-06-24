package ie.zalando.fabric.gateway.util

import akka.http.scaladsl.model.Uri

import scala.util.matching.Regex

object Util {
  val UNESCAPED_QUOTATION_MARK_RE: Regex = "(?<!\\\\)(\")".r
  val ESCAPED_QUOTATION_MARK             = "\\\\\""

  def escapeQuotes(str: String) = UNESCAPED_QUOTATION_MARK_RE.replaceAllIn(str, ESCAPED_QUOTATION_MARK)

  def parseCorsUri(uriString: String): Uri = {
    val withoutScheme = uriString.replace("http://", "").replace("https://", "")
    val parts         = withoutScheme.split(":")
    Uri.from(host = parts.head, port = parts.lift(1).map(_.toInt).getOrElse(0))
  }

}
