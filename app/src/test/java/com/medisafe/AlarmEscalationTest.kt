package com.medisafe

import com.medisafe.data.model.Priority
import com.medisafe.data.model.ReminderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the rule that high/urgent reminders always ring like an alarm, which is what
 * makes them audible with the screen off.
 */
class AlarmEscalationTest {

    private fun item(
        priority: Priority = Priority.NORMAL,
        alertAsAlarm: Boolean = false
    ) = ReminderItem(
        title = "Metformin",
        scheduledTimeMillis = 1_700_000_000_000L,
        priority = priority.name,
        alertAsAlarm = alertAsAlarm
    )

    @Test
    fun highPriorityRingsAsAlarmWithoutTheSwitch() {
        assertTrue(item(priority = Priority.HIGH).ringsAsAlarm)
    }

    @Test
    fun urgentPriorityRingsAsAlarmWithoutTheSwitch() {
        assertTrue(item(priority = Priority.URGENT).ringsAsAlarm)
    }

    @Test
    fun normalPriorityOnlyRingsWhenSwitchIsOn() {
        assertFalse(item(priority = Priority.NORMAL).ringsAsAlarm)
        assertTrue(item(priority = Priority.NORMAL, alertAsAlarm = true).ringsAsAlarm)
    }

    @Test
    fun lowPriorityNeverRingsByItself() {
        assertFalse(item(priority = Priority.LOW).ringsAsAlarm)
    }

    @Test
    fun onlyUrgentIsCritical() {
        assertTrue(item(priority = Priority.URGENT).isCritical)
        assertFalse(item(priority = Priority.HIGH).isCritical)
        assertFalse(item(priority = Priority.NORMAL, alertAsAlarm = true).isCritical)
    }

    @Test
    fun priorityDrivenAlarmsExplainThemselves() {
        assertEquals("Urgent priority · always rings", item(priority = Priority.URGENT).alarmReasonLabel)
        assertEquals("High priority · always rings", item(priority = Priority.HIGH).alarmReasonLabel)
        assertEquals("Ring like an alarm", item(alertAsAlarm = true).alarmReasonLabel)
        assertEquals("", item().alarmReasonLabel)
    }

    @Test
    fun stickyAlertStillMatchesHighAndUrgent() {
        assertTrue(item(priority = Priority.HIGH).isStickyAlert())
        assertTrue(item(priority = Priority.URGENT).isStickyAlert())
        assertFalse(item(priority = Priority.NORMAL).isStickyAlert())
    }
}
