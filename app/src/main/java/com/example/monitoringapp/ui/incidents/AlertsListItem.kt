package com.example.monitoringapp.ui.incidents

import com.example.monitoringapp.domain.model.Incident

sealed class AlertsListItem {
    data class Featured(val ui: FeaturedIncidentUi) : AlertsListItem()
    data object Section : AlertsListItem()
    data class Row(val incident: Incident) : AlertsListItem()
}

fun buildAlertsList(ui: FeaturedIncidentUi, others: List<Incident>): List<AlertsListItem> {
    if (ui.incident == null) return emptyList()
    val items = mutableListOf<AlertsListItem>(AlertsListItem.Featured(ui))
    if (others.isNotEmpty()) {
        items.add(AlertsListItem.Section)
        others.forEach { items.add(AlertsListItem.Row(it)) }
    }
    return items
}
