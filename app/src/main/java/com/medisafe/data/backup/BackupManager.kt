package com.medisafe.data.backup

import com.medisafe.data.model.ReminderItem
import com.medisafe.data.model.ReminderLog
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {
    fun exportJson(reminders: List<ReminderItem>, logs: List<ReminderLog>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("reminders", JSONArray().also { array ->
            reminders.forEach { array.put(reminderToJson(it)) }
        })
        root.put("logs", JSONArray().also { array ->
            logs.forEach { array.put(logToJson(it)) }
        })
        return root.toString(2)
    }

    fun importJson(raw: String): BackupPayload {
        val root = JSONObject(raw)
        val reminders = mutableListOf<ReminderItem>()
        val reminderArray = root.optJSONArray("reminders") ?: JSONArray()
        for (i in 0 until reminderArray.length()) {
            reminders += reminderFromJson(reminderArray.getJSONObject(i))
        }
        val logs = mutableListOf<ReminderLog>()
        val logArray = root.optJSONArray("logs") ?: JSONArray()
        for (i in 0 until logArray.length()) {
            logs += logFromJson(logArray.getJSONObject(i))
        }
        return BackupPayload(reminders, logs)
    }

    private fun reminderToJson(item: ReminderItem): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("category", item.category)
            put("dosageOrDetails", item.dosageOrDetails)
            put("scheduledTimeMillis", item.scheduledTimeMillis)
            put("recurrence", item.recurrence)
            put("customIntervalHours", item.customIntervalHours)
            put("isCompleted", item.isCompleted)
            put("completedAtMillis", item.completedAtMillis)
            put("priority", item.priority)
            put("iconName", item.iconName)
            put("colorHex", item.colorHex)
            put("notificationSound", item.notificationSound)
            put("vibrate", item.vibrate)
            put("createdAtMillis", item.createdAtMillis)
            put("isActive", item.isActive)
            put("snoozedUntilMillis", item.snoozedUntilMillis)
            put("doseTimes", item.doseTimes)
            put("lastAcknowledgedMillis", item.lastAcknowledgedMillis)
            put("pillsRemaining", item.pillsRemaining)
            put("refillThreshold", item.refillThreshold)
            put("courseEndMillis", item.courseEndMillis)
            put("weekdaysMask", item.weekdaysMask)
            put("isPrn", item.isPrn)
            put("prnMaxPerDay", item.prnMaxPerDay)
            put("foodTiming", item.foodTiming)
            put("expiryMillis", item.expiryMillis)
            put("strength", item.strength)
            put("form", item.form)
            put("pharmacyName", item.pharmacyName)
            put("pharmacyPhone", item.pharmacyPhone)
            put("doctorName", item.doctorName)
            put("doctorPhone", item.doctorPhone)
        }
    }

    private fun reminderFromJson(json: JSONObject): ReminderItem {
        return ReminderItem(
            id = 0L,
            title = json.optString("title"),
            category = json.optString("category"),
            dosageOrDetails = json.optString("dosageOrDetails"),
            scheduledTimeMillis = json.optLong("scheduledTimeMillis"),
            recurrence = json.optString("recurrence"),
            customIntervalHours = json.optInt("customIntervalHours"),
            isCompleted = json.optBoolean("isCompleted"),
            completedAtMillis = json.optLongOrNull("completedAtMillis"),
            priority = json.optString("priority"),
            iconName = json.optString("iconName", "pill"),
            colorHex = json.optLong("colorHex", 0xFF0D9488),
            notificationSound = json.optBoolean("notificationSound", true),
            vibrate = json.optBoolean("vibrate", true),
            createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
            isActive = json.optBoolean("isActive", true),
            snoozedUntilMillis = json.optLongOrNull("snoozedUntilMillis"),
            doseTimes = json.optString("doseTimes"),
            lastAcknowledgedMillis = json.optLongOrNull("lastAcknowledgedMillis"),
            pillsRemaining = if (json.has("pillsRemaining") && !json.isNull("pillsRemaining")) {
                json.optInt("pillsRemaining")
            } else null,
            refillThreshold = json.optInt("refillThreshold", 5),
            courseEndMillis = json.optLongOrNull("courseEndMillis"),
            weekdaysMask = json.optInt("weekdaysMask", 0),
            isPrn = json.optBoolean("isPrn", false),
            prnMaxPerDay = json.optInt("prnMaxPerDay", 3),
            foodTiming = json.optString("foodTiming", "NONE"),
            expiryMillis = json.optLongOrNull("expiryMillis"),
            strength = json.optString("strength"),
            form = json.optString("form", "NONE"),
            pharmacyName = json.optString("pharmacyName"),
            pharmacyPhone = json.optString("pharmacyPhone"),
            doctorName = json.optString("doctorName"),
            doctorPhone = json.optString("doctorPhone")
        )
    }

    private fun logToJson(log: ReminderLog): JSONObject {
        return JSONObject().apply {
            put("reminderTitle", log.reminderTitle)
            put("category", log.category)
            put("action", log.action)
            put("timestampMillis", log.timestampMillis)
            put("note", log.note)
        }
    }

    private fun logFromJson(json: JSONObject): ReminderLog {
        return ReminderLog(
            reminderId = 0L,
            reminderTitle = json.optString("reminderTitle"),
            category = json.optString("category"),
            action = json.optString("action"),
            timestampMillis = json.optLong("timestampMillis"),
            note = json.optString("note")
        )
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        val value = optLong(key, Long.MIN_VALUE)
        return if (value == Long.MIN_VALUE) null else value
    }
}

data class BackupPayload(
    val reminders: List<ReminderItem>,
    val logs: List<ReminderLog>
)
