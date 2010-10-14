package com.twitter.killdeer

object Main {
  def main(args: Array[String]) {
    val mode = args(0)
    mode match {
      case "killdeer" => Killdeer(args.drop(1))
      case "deerkill" => Deerkill(args.drop(1))
    }
  }
}