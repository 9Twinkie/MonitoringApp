package com.example.monitoringapp.ui.incidents

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.ItemAlertsSectionBinding
import com.example.monitoringapp.databinding.ItemIncidentBinding
import com.example.monitoringapp.databinding.LayoutFeaturedIncidentBinding
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.utils.SeverityUiHelper

class AlertsAdapter(
    private val currentUsername: () -> String?,
    private val onFeaturedReady: (LayoutFeaturedIncidentBinding) -> Unit,
    private val onAccept: (Incident) -> Unit,
    private val onComplete: (Incident) -> Unit,
    private val onClose: (Incident) -> Unit,
    private val onSelect: (Incident) -> Unit,
    private val onOpenDetail: (Incident) -> Unit
) : ListAdapter<AlertsListItem, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val TYPE_FEATURED = 0
        private const val TYPE_SECTION = 1
        private const val TYPE_ROW = 2
        const val PAYLOAD_CHART = "chart"
        const val PAYLOAD_TEXT = "text"
    }

    private var featuredBinding: LayoutFeaturedIncidentBinding? = null

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is AlertsListItem.Featured -> TYPE_FEATURED
        is AlertsListItem.Section -> TYPE_SECTION
        is AlertsListItem.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FEATURED -> FeaturedVH(
                LayoutFeaturedIncidentBinding.inflate(inflater, parent, false)
            )
            TYPE_SECTION -> SectionVH(
                ItemAlertsSectionBinding.inflate(inflater, parent, false)
            )
            else -> RowVH(
                ItemIncidentBinding.inflate(inflater, parent, false),
                currentUsername,
                onAccept,
                onComplete,
                onClose,
                onSelect,
                onOpenDetail
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AlertsListItem.Featured -> (holder as FeaturedVH).bindFull(item.ui)
            is AlertsListItem.Row -> (holder as RowVH).bind(item.incident)
            is AlertsListItem.Section -> Unit
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val item = getItem(position)
        if (holder is FeaturedVH && item is AlertsListItem.Featured) {
            when (payloads.lastOrNull()) {
                "both" -> holder.bindFull(item.ui)
                PAYLOAD_TEXT -> holder.bindTexts(item.ui)
                PAYLOAD_CHART -> holder.bindChart(item.ui)
            }
        }
    }

    fun featuredBinding(): LayoutFeaturedIncidentBinding? = featuredBinding

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is FeaturedVH && featuredBinding === holder.binding) {
            featuredBinding = null
        }
    }

    inner class FeaturedVH(
        val binding: LayoutFeaturedIncidentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            featuredBinding = binding
            onFeaturedReady(binding)
        }

        fun bindFull(ui: FeaturedIncidentUi) {
            bindTexts(ui)
            bindChart(ui)
        }

        fun bindTexts(ui: FeaturedIncidentUi) {
            val incident = ui.incident ?: return
            IncidentUiHelper.bindTexts(binding, incident, currentUsername())
        }

        fun bindChart(ui: FeaturedIncidentUi) {
            val incident = ui.incident ?: return
            IncidentUiHelper.bindChart(
                binding,
                incident,
                ui.chart.points,
                ui.chart.threshold,
                ui.chartLoading
            )
        }
    }

    class SectionVH(binding: ItemAlertsSectionBinding) : RecyclerView.ViewHolder(binding.root)

    class RowVH(
        private val binding: ItemIncidentBinding,
        private val currentUsername: () -> String?,
        private val onAccept: (Incident) -> Unit,
        private val onComplete: (Incident) -> Unit,
        private val onClose: (Incident) -> Unit,
        private val onSelect: (Incident) -> Unit,
        private val onOpenDetail: (Incident) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Incident) {
            binding.tvTitle.text = item.title
            SeverityUiHelper.applyBadge(binding.tvSeverity, item.severity)
            binding.tvStatus.setText(IncidentUiHelper.statusLabelRes(item.status))
            binding.tvId.text = "#${item.id}"

            if (item.status == IncidentStatus.CLOSED) {
                binding.tvAssignedEngineer.isVisible = true
                val parts = buildList {
                    item.closedByUsername?.let {
                        add(binding.root.context.getString(R.string.closed_by, it))
                    }
                    item.assignedEngineerUsername?.let {
                        add(binding.root.context.getString(R.string.executed_by, it))
                    }
                    item.closeComment?.let {
                        add(binding.root.context.getString(R.string.close_comment_label, it))
                    }
                }
                binding.tvAssignedEngineer.text = parts.joinToString(" · ")
            } else {
                val username = item.assignedEngineerUsername?.takeIf { it.isNotBlank() }
                val showAssigned = username != null || item.status.isInProgress()
                binding.tvAssignedEngineer.isVisible = showAssigned
                if (showAssigned) {
                    binding.tvAssignedEngineer.text = if (username != null) {
                        binding.root.context.getString(R.string.assigned_engineer, username)
                    } else {
                        binding.root.context.getString(R.string.assigned_engineer_unknown)
                    }
                }
            }

            val actions = IncidentActionHelper.actionsFor(item, currentUsername())
            binding.btnAccept.isVisible = actions.showAccept
            binding.btnConfirm.isVisible = actions.showComplete
            binding.btnClose.isVisible = actions.showClose
            binding.btnAccept.setOnClickListener { onAccept(item) }
            binding.btnConfirm.setOnClickListener { onComplete(item) }
            binding.btnClose.setOnClickListener { onClose(item) }
            binding.root.setOnClickListener { onSelect(item) }
            binding.root.setOnLongClickListener {
                onOpenDetail(item)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AlertsListItem>() {
        override fun areItemsTheSame(old: AlertsListItem, new: AlertsListItem): Boolean {
            return when {
                old is AlertsListItem.Featured && new is AlertsListItem.Featured -> true
                old is AlertsListItem.Section && new is AlertsListItem.Section -> true
                old is AlertsListItem.Row && new is AlertsListItem.Row -> old.incident.id == new.incident.id
                else -> false
            }
        }

        override fun areContentsTheSame(old: AlertsListItem, new: AlertsListItem): Boolean {
            return when {
                old is AlertsListItem.Featured && new is AlertsListItem.Featured ->
                    old.ui.contentKey() == new.ui.contentKey() &&
                        old.ui.chartKey() == new.ui.chartKey()
                old is AlertsListItem.Row && new is AlertsListItem.Row ->
                    old.incident == new.incident
                else -> old == new
            }
        }

        override fun getChangePayload(oldItem: AlertsListItem, newItem: AlertsListItem): Any? {
            if (oldItem is AlertsListItem.Featured && newItem is AlertsListItem.Featured) {
                val textChanged = oldItem.ui.contentKey() != newItem.ui.contentKey()
                val chartChanged = oldItem.ui.chartKey() != newItem.ui.chartKey()
                return when {
                    textChanged && chartChanged -> "both"
                    chartChanged -> PAYLOAD_CHART
                    textChanged -> PAYLOAD_TEXT
                    else -> null
                }
            }
            return super.getChangePayload(oldItem, newItem)
        }
    }
}
