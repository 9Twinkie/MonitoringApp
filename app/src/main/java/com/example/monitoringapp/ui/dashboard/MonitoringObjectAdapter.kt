package com.example.monitoringapp.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.ItemMonitoringObjectBinding
import com.example.monitoringapp.domain.model.MonitoringObject

class MonitoringObjectAdapter :
    ListAdapter<MonitoringObject, MonitoringObjectAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMonitoringObjectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val binding: ItemMonitoringObjectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MonitoringObject) {
            binding.tvName.text = item.name
            binding.tvHost.text = item.host
            binding.tvMetric.text = item.metricSummary
            val color = if (item.isHealthy) R.color.accent_green else R.color.accent_red
            binding.statusDot.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, color)
            )
        }
    }

    private object Diff : DiffUtil.ItemCallback<MonitoringObject>() {
        override fun areItemsTheSame(old: MonitoringObject, new: MonitoringObject) =
            old.host == new.host

        override fun areContentsTheSame(old: MonitoringObject, new: MonitoringObject) =
            old == new
    }
}
