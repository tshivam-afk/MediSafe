package com.medisafe

import com.medisafe.data.model.Priority
import com.medisafe.data.model.ReminderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the rule that ringing like an alarm is purely opt-in per reminder — priority
 * alone never forces it. Urgent items additionally get the critical audio path (max
 * volume, no fade-in) but only when the reminder itself opted in.
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
    fun highPriorityDoesNotForceAlarmRinging() {
        assertFalse(item(priority = Priority.HIGH).ringsAsAlarm)
        assertTrue(item(priority = Priority.HIGH, alertAsAlarm = true).ringsAsAlarm)
    }

    @Test
    fun urgentPriorityDoesNotForceAlarmRinging() {
        assertFalse(item(priority = Priority.URGENT).ringsAsAlarm)
        assertTrue(item(priority = Priority.URGENT, alertAsAlarm = true).ringsAsAlarm)
    }

    @Test
    fun ringingIsOptInForEachReminder() {
        assertFalse(item().ringsAsAlarm)
        assertFalse(item(priority = Priority.NORMAL).ringsAsAlarm)
        assertFalse(item(priority = Priority.LOW).ringsAsAlarm)
        assertTrue(item(alertAsAlarm = true).ringsAsAlarm)
    }

    @Test
    fun onlyUrgentIsCritical() {
        assertTrue(item(priority = Priority.URGENT, alertAsAlarm = true).isCritical)
        assertFalse(item(priority = Priority.HIGH, alertAsAlarm = true).isCritical)
        assertFalse(item(alertAsAlarm = true).isCritical)
        assertFalse(item(priority = Priority.URGENT).isCritical)
    }

    @Test
    fun alarmReasonLabelFollowsTheSwitchNotPriority() {
        assertEquals("Ring like an alarm", item(alertAsAlarm = true).alarmReasonLabel)
        assertEquals("", item().alarmReasonLabel)
        assertEquals("", item(priority = Priority.URGENT).alarmReasonLabel)
        assertEquals("", item(priority = Priority.HIGH).alarmReasonLabel)
    }

    @Test
    fun stickyAlertStillMatchesHighAndUrgent() {
        assertTrue(item(priority = Priority.HIGH).isStickyAlert())
        assertTrue(item(priority = Priority.URGENT).isStickyAlert())
        assertFalse(item().isStickyAlert())
    }
}
