package com.example.aws_g4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimulateModeTest {

    @Test fun `NONE has null wire value`() = assertNull(SimulateMode.NONE.wire)

    @Test fun `TRANSIENT maps to TRANSIENT wire value`() =
        assertEquals("TRANSIENT", SimulateMode.TRANSIENT.wire)

    @Test fun `POISON maps to POISON wire value`() =
        assertEquals("POISON", SimulateMode.POISON.wire)

    @Test fun `each mode has a label`() {
        SimulateMode.values().forEach { mode ->
            assert(mode.label.isNotBlank())
        }
    }
}