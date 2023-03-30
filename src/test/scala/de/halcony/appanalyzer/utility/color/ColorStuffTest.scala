package de.halcony.appanalyzer.utility.color

import ColorStuff.RGB
import org.scalactic.TolerantNumerics
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ColorStuffTest  extends AnyWordSpec with Matchers {

  implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.01)

  //numbers for lab are derived by http://colormine.org/convert/rgb-to-lab

  "rgbToLab" should {
    "be correct for pure red" in {
      val lab = ColorStuff.rgbToLab(RGB(255,0,0))
      lab.L.toInt shouldBe 53
      lab.a.toInt shouldBe 80
      lab.b.toInt shouldBe 67
    }
    "be correct for pure green" in {
      val lab = ColorStuff.rgbToLab(RGB(0, 255, 0))
      lab.L.toInt shouldBe 87
      lab.a.toInt shouldBe -86
      lab.b.toInt shouldBe 83
    }
    "be correct for pure blue" in {
      val lab = ColorStuff.rgbToLab(RGB(0, 0, 255))
      lab.L.toInt shouldBe 32
      lab.a.toInt shouldBe 79
      lab.b.toInt shouldBe -107
    }
    "be correct for petrol" in {
      val lab = ColorStuff.rgbToLab(RGB(0, 95, 106))
      lab.L.toInt shouldBe 36
      lab.a.toInt shouldBe -19
      lab.b.toInt shouldBe -13
    }
    "be correct for magenta" in {
      val lab = ColorStuff.rgbToLab(RGB(255, 0, 255))
      lab.L.toInt shouldBe 60
      lab.a.toInt shouldBe 98
      lab.b.toInt shouldBe -60
    }
    "be correct for white" in {
      val lab = ColorStuff.rgbToLab(RGB(255, 255, 255))
      lab.L.toInt shouldBe 100
      lab.a.toInt shouldBe 0
      lab.b.toInt shouldBe 0
    }
    "be correct for black" in {
      val lab = ColorStuff.rgbToLab(RGB(0, 0, 0))
      lab.L.toInt shouldBe 0
      lab.a.toInt shouldBe 0
      lab.b.toInt shouldBe 0
    }
  }

  // numbers derived from http://www.brucelindbloom.com/index.html?ColorDifferenceCalc.html
  // leveraging the above unit tests
  "deltaE" should {
    "be correct for pure red on red" in {
      ColorStuff.deltaE(RGB(255,0,0),RGB(255,0,0)).toInt shouldBe 0
    }
    "be correct for pure red on green" in {
      ColorStuff.deltaE(RGB(255,0,0),RGB(0,255,0)).toInt shouldBe 86
    }
    "be correct for pure red on blue" in {
      ColorStuff.deltaE(RGB(255,0,0),RGB(0,0,255)).toInt shouldBe 52
    }
    "be correct for petrol on magenta" in {
      ColorStuff.deltaE(RGB(0,95,106),RGB(255,0,255)).toInt shouldBe 41
    }
    "be correct for pure green on white" in {
      ColorStuff.deltaE(RGB(0,255,0),RGB(255,255,255)).toInt shouldBe 33
    }
    "be correct for pure black on white" in {
      ColorStuff.deltaE(RGB(0, 0, 0), RGB(255, 255, 255)).toInt shouldBe 100
    }
  }

}
