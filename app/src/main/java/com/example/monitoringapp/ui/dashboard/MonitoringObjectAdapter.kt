package com.example.monitoringapp.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.ItemMonitoringObjectBinding
import com.example.monitoringapp.domain.model.MonitoringObject
import com.example.monitoringapp.utils.SeverityUiHelper

class MonitoringObjectAdapter(
    private val onFavoriteClick: (MonitoringObject) -> Unit,
    private val onObjectClick: (MonitoringObject) -> Unit
) : ListAdapter<MonitoringObject, MonitoringObjectAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMonitoringObjectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding, onFavoriteClick, onObjectClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        private val binding: ItemMonitoringObjectBinding,
        private val onFavoriteClick: (MonitoringObject) -> Unit,
        private val onObjectClick: (MonitoringObject) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MonitoringObject) {
            binding.tvName.text = item.name
            binding.tvHost.text = item.host
            binding.tvMetric.text = item.metricSummary
            binding.tvMetric.setTextColor(
                SeverityUiHelper.metricSummaryColor(binding.root.context, item.isHealthy)
            )

            binding.tvIncidents.isVisible = item.openIncidents > 0
            if (item.openIncidents > 0) {
                binding.tvIncidents.text =
                    binding.root.context.getString(R.string.incidents_count, item.openIncidents)
            }

            binding.statusDot.setBackgroundColor(
                SeverityUiHelper.statusDotColor(binding.root.context, item.isHealthy)
            )
            SeverityUiHelper.applySeverityStripe(binding.severityStripe, item.worstSeverity)
            SeverityUiHelper.applyCardAccent(binding.cardObject, item.worstSeverity)

            binding.btnFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            binding.btnFavorite.contentDescription = binding.root.context.getString(
                if (item.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
            )
            binding.btnFavorite.setOnClickListener { onFavoriteClick(item) }
            binding.cardObject.setOnClickListener { onObjectClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MonitoringObject>() {
        override fun areItemsTheSame(old: MonitoringObject, new: MonitoringObject) =
            old.targetKey == new.targetKey

        override fun areContentsTheSame(old: MonitoringObject, new: MonitoringObject) =
            old == new
    }
}
