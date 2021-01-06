package ie.zalando.fabric.gateway.util

import scala.util.matching.Regex

object Util {
  val UNESCAPED_QUOTATION_MARK_RE: Regex = "(?<!\\\\)(\")".r
  val ESCAPED_QUOTATION_MARK             = "\\\\\""

  def escapeQuotes(str: String) = UNESCAPED_QUOTATION_MARK_RE.replaceAllIn(str, ESCAPED_QUOTATION_MARK)
}
