package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Test

class LookRulesTest {

    private fun look(s: String) = LookRules.detect(s)

    @Test fun lookVerbWithDirection() {
        assertEquals(Look.LEFT, look("Look to the left."))
        assertEquals(Look.RIGHT, look("Glance over to the right, please"))
        assertEquals(Look.UP, look("Can you look up for me?"))
        assertEquals(Look.DOWN, look("Look down at my hands."))
        assertEquals(Look.UP, look("Look up at the ceiling."))
        assertEquals(Look.DOWN, look("Check the floor below you."))
        assertEquals(Look.RIGHT, look("Turn your eyes to the right."))
    }

    @Test fun relativeSideWithoutVerb() {
        assertEquals(Look.RIGHT, look("There's a cat on your right!"))
        assertEquals(Look.LEFT, look("Someone is standing to your left"))
    }

    @Test fun negatedRequestsStayCentered() {
        assertEquals(Look.CENTER, look("Don't look left."))
        assertEquals(Look.CENTER, look("Do not look down at the floor"))
        assertEquals(Look.CENTER, look("Stop looking up!"))
    }

    @Test fun codexReviewCases() {
        assertEquals(Look.CENTER, look("Look up the invoice number."))
        assertEquals(Look.CENTER, look("I am looking forward to lying down."))
        assertEquals(Look.CENTER, look("Do not ever look left."))
        assertEquals(Look.CENTER, look("No need to look to your left."))
        assertEquals(Look.LEFT, look("Look over there, to your left, a bird!"))
        assertEquals(Look.LEFT, look("Look left, someone is at the door."))
        assertEquals(Look.LEFT, look("Look to left side please."))
        assertEquals(Look.CENTER, look("Look up a phone number."))
        assertEquals(Look.UP, look("Look slightly over at the ceiling."))  // 4 linking words
        assertEquals(Look.CENTER, look("Look a bit more over there toward the left"))  // 5+ words: too far
    }

    @Test fun nonGazeSentencesStayCentered() {
        assertEquals(Look.CENTER, look("Please turn the volume down a little."))
        assertEquals(Look.CENTER, look("Right, the blue folder."))
        assertEquals(Look.CENTER, look("I got the job I wanted!"))
        assertEquals(Look.CENTER, look("The meeting moved up to Friday."))
        assertEquals(Look.CENTER, look("I'm feeling down today."))
        assertEquals(Look.CENTER, look("Left my keys at home."))
    }
}
