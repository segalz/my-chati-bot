package app.mychatibot.eyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeIntentParserTest {

    private fun parse(json: String, now: Long = 1_000) = EyeIntentParser.parse(json, IntentSource.DEBUG, now)

    private fun valid(json: String, now: Long = 1_000): EyeIntent {
        val r = parse(json, now)
        assertTrue("expected valid, got $r", r is ParseResult.Valid)
        return (r as ParseResult.Valid).intent
    }

    private fun rejected(json: String): String {
        val r = parse(json)
        assertTrue("expected rejection, got $r", r is ParseResult.Rejected)
        return (r as ParseResult.Rejected).reason
    }

    @Test fun minimalIntent() {
        val i = valid("""{"schema_version":1,"expression":"HAPPY","look":"center"}""")
        assertEquals(Expression.HAPPY, i.expression)
        assertEquals(Look.CENTER, i.look)
        assertEquals(null, i.holdMs)
        assertEquals(null, i.expiresAtMs)
    }

    @Test fun allFields() {
        val i = valid(
            """{"schema_version":1,"expression":"WINK_LEFT","look":"left","hold_ms":2000,
               "session_id":"s-12_a","expires_in_ms":5000}""",
            now = 10_000,
        )
        assertEquals(Expression.WINK_LEFT, i.expression)
        assertEquals(Look.LEFT, i.look)
        assertEquals(2000L, i.holdMs)
        assertEquals("s-12_a", i.sessionId)
        assertEquals(15_000L, i.expiresAtMs)
    }

    @Test fun noneIsAValidExpression() {
        assertEquals(Expression.NONE, valid("""{"schema_version":1,"expression":"NONE","look":"center"}""").expression)
    }

    @Test fun everyExpressionAndLookParses() {
        for (e in Expression.entries) for (l in Look.entries) {
            val i = valid("""{"schema_version":1,"expression":"${e.name}","look":"${l.name.lowercase()}"}""")
            assertEquals(e, i.expression)
            assertEquals(l, i.look)
        }
    }

    @Test fun invalidEnumsRejected() {
        rejected("""{"schema_version":1,"expression":"JOYFUL","look":"center"}""")
        rejected("""{"schema_version":1,"expression":"happy","look":"center"}""")   // case matters
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"LEFT"}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"behind"}""")
    }

    @Test fun missingOrWrongTypedFieldsRejected() {
        rejected("""{"schema_version":1,"look":"center"}""")
        rejected("""{"schema_version":1,"expression":"HAPPY"}""")
        rejected("""{"expression":"HAPPY","look":"center"}""")
        rejected("""{"schema_version":"1","expression":"HAPPY","look":"center"}""")
        rejected("""{"schema_version":1,"expression":3,"look":"center"}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","session_id":7}""")
    }

    @Test fun wrongSchemaVersionRejected() {
        rejected("""{"schema_version":2,"expression":"HAPPY","look":"center"}""")
    }

    @Test fun unknownFieldsRejected() {
        val reason = rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","camera":"on"}""")
        assertTrue(reason, reason.contains("camera"))
    }

    @Test fun nonIntegerAndNanNumbersRejected() {
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","hold_ms":1500.5}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","hold_ms":NaN}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","hold_ms":"2000"}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","hold_ms":true}""")
    }

    @Test fun outOfRangeRejectedNotClamped() {
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","hold_ms":100}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","hold_ms":60000}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","hold_ms":-5}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","expires_in_ms":0}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","expires_in_ms":999999}""")
    }

    @Test fun badSessionIdRejected() {
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","session_id":"a b"}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","session_id":"${"x".repeat(65)}"}""")
    }

    @Test fun malformedAndNonObjectRejected() {
        rejected("""{"schema_version":1,"expression":"HAPPY",""")
        rejected("""["HAPPY"]""")
        rejected("HAPPY")
        rejected("")
    }

    @Test fun trailingContentRejected() {
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center"} garbage""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center"}{"x":1}""")
    }

    @Test fun duplicateKeysRejected() {
        val reason = rejected("""{"schema_version":1,"expression":"HAPPY","expression":"SAD","look":"center"}""")
        assertTrue(reason, reason.contains("duplicate"))
    }

    @Test fun nestedValuesRejected() {
        rejected("""{"schema_version":1,"expression":{"name":"HAPPY"},"look":"center"}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":["center"]}""")
    }

    @Test fun nonStandardSyntaxRejected() {
        rejected("""{'schema_version':1,'expression':'HAPPY','look':'center'}""")
        rejected("""{schema_version:1,"expression":"HAPPY","look":"center"}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center",}""")
        rejected("""{"schema_version":01,"expression":"HAPPY","look":"center"}""")
    }

    @Test fun hugeNumbersRejected() {
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","hold_ms":99999999999999999999999}""")
        rejected("""{"schema_version":1,"expression":"HAPPY","look":"center","hold_ms":2e3}""")
    }

    @Test fun escapesAndWhitespaceAccepted() {
        val i = valid(" \n{ \"schema_version\" : 1 ,\t\"expression\":\"H\\u0041PPY\", \"look\":\"left\" }\r\n")
        assertEquals(Expression.HAPPY, i.expression)
    }

    @Test fun modelMayNotSetHold() {
        val r = EyeIntentParser.parse(
            """{"schema_version":1,"expression":"HAPPY","look":"center","hold_ms":2000}""", IntentSource.MODEL, 0,
        )
        assertTrue("$r", r is ParseResult.Rejected)
        val ok = EyeIntentParser.parse("""{"schema_version":1,"expression":"HAPPY","look":"center"}""", IntentSource.MODEL, 0)
        assertTrue("$ok", ok is ParseResult.Valid)
    }

    @Test fun modelV4OutputIsWrappedAndValidated() {
        val wrapped = EyeIntentParser.wrapModelOutput("""{"expression":"SAD","look":"down"}""")
        val r = EyeIntentParser.parse(wrapped, IntentSource.MODEL, 0)
        assertTrue("$r", r is ParseResult.Valid)
        assertEquals(Expression.SAD, (r as ParseResult.Valid).intent.expression)
        assertEquals(Look.DOWN, r.intent.look)
        // Wrapping never makes bad output valid.
        for (bad in listOf("""{"expression":"SAD"}""", """{"expression":"SAD","look":"down","hold_ms":500}""",
                           """{}""", "SAD", """{"expression":"SAD","look":"down"} x""",
                           """{"expression":"SAD","look":"down"}""" + " ".repeat(EyeIntentParser.MAX_PAYLOAD_CHARS),
                           " {\"expression\":\"SAD\",\"look\":\"down\"}",
                           "{\"expression\":\"SAD\",\"look\":\"down\"} ")) {
            val rb = EyeIntentParser.parse(EyeIntentParser.wrapModelOutput(bad), IntentSource.MODEL, 0)
            assertTrue("$bad -> $rb", rb is ParseResult.Rejected)
        }
    }

    @Test fun unrepresentableDeadlineRejected() {
        val r = EyeIntentParser.parse(
            """{"schema_version":1,"expression":"HAPPY","look":"center","expires_in_ms":5000}""",
            IntentSource.DEBUG, Long.MAX_VALUE - 10,
        )
        assertTrue("$r", r is ParseResult.Rejected)
    }

    @Test fun oversizedPayloadRejected() {
        val pad = " ".repeat(EyeIntentParser.MAX_PAYLOAD_CHARS)
        val reason = rejected("""{"schema_version":1,"expression":"HAPPY","look":"center"}$pad""")
        assertTrue(reason, reason.contains("longer"))
    }
}
