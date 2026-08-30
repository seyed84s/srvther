package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerFooterBinding
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.viewmodel.MainViewModel
import java.util.Collections

class MainRecyclerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(), ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private val doubleColumnDisplay = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    private var data: MutableList<ServersCache> = mutableListOf()

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: MutableList<ServersCache>?, position: Int = -1) {
        data = newData?.toMutableList() ?: mutableListOf()

        if (position >= 0 && position in data.indices) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = data.size + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val context = holder.itemMainBinding.root.context
            val guid = data[position].guid
            val profile = data[position].profile
            val isSelected = guid == MmkvManager.getSelectServer()

            // Server card selection highlight
            val strokeWidthPx = (if (isSelected) 2 else 1) * context.resources.displayMetrics.density
            holder.itemMainBinding.cardItem.strokeWidth = strokeWidthPx.toInt()
            holder.itemMainBinding.cardItem.strokeColor = ContextCompat.getColor(
                context,
                if (isSelected) R.color.srvx_card_stroke_selected else R.color.srvx_card_stroke
            )
            holder.itemMainBinding.imgRadio.setImageResource(
                if (isSelected) R.drawable.srvx_radio_active else R.drawable.srvx_radio_inactive
            )

            // Name with country flag & address
            holder.itemMainBinding.tvName.text = com.v2ray.ang.srvx.CountryFlagUtil.formatRemarksWithFlag(profile.remarks, profile.server)
            holder.itemMainBinding.tvStatistics.text = getAddress(profile)
            holder.itemMainBinding.tvType.text = getProtocolDescription(profile)

            // TestResult / Latency styling
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            val delayStr = aff?.getTestDelayString().orEmpty()
            val delayMillis = aff?.testDelayMillis ?: 0L

            if (delayStr.isEmpty()) {
                holder.itemMainBinding.tvTestResult.text = "—"
                holder.itemMainBinding.tvTestResult.setBackgroundResource(R.drawable.srvx_pill_ping_neutral)
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.srvx_text_muted))
            } else if (delayMillis < 0L) {
                holder.itemMainBinding.tvTestResult.text = delayStr
                holder.itemMainBinding.tvTestResult.setBackgroundResource(R.drawable.srvx_pill_ping_red)
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPingRed))
            } else if (delayMillis <= 250L) {
                holder.itemMainBinding.tvTestResult.text = delayStr
                holder.itemMainBinding.tvTestResult.setBackgroundResource(R.drawable.srvx_pill_ping_green)
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPing))
            } else if (delayMillis <= 500L) {
                holder.itemMainBinding.tvTestResult.text = delayStr
                holder.itemMainBinding.tvTestResult.setBackgroundResource(R.drawable.srvx_pill_ping_amber)
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPingAmber))
            } else {
                holder.itemMainBinding.tvTestResult.text = delayStr
                holder.itemMainBinding.tvTestResult.setBackgroundResource(R.drawable.srvx_pill_ping_red)
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPingRed))
            }

            // Click listener with tactile haptic feedback
            holder.itemMainBinding.cardItem.setOnClickListener {
                com.v2ray.ang.srvx.SrvxHaptics.tick(context, holder.itemMainBinding.cardItem)
                adapterListener?.onSelectServer(guid)
            }
        }
    }

    private fun getAddress(profile: ProfileItem): String {
        return profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
    }

    private fun getSubscriptionRemarks(profile: ProfileItem): String {
        val subRemarks =
            if (mainViewModel.subscriptionId.isEmpty())
                MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
            else
                null
        return subRemarks?.toString() ?: ""
    }

    private fun getProtocolDescription(profile: ProfileItem): String {
        if (profile.configType.isComplexType()) {
            return profile.configType.name
        }

        val parts = mutableListOf<String>()
        parts.add(profile.configType.name)

        profile.network?.let { net ->
            if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) {
                parts.add(net)
            }
        }

        profile.security?.let { sec ->
            if (sec.isNotBlank() && !sec.equals("tls", ignoreCase = true)) {
                parts.add(sec)
            }
        }

        return parts.joinToString(" / ")
    }

    fun removeServerSub(guid: String, position: Int) {
        val idx = data.indexOfFirst { it.guid == guid }
        if (idx >= 0) {
            data.removeAt(idx)
            notifyItemRemoved(idx)
            notifyItemRangeChanged(idx, data.size - idx)
        }
    }

    fun setSelectServer(fromPosition: Int, toPosition: Int) {
        notifyItemChanged(fromPosition)
        notifyItemChanged(toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))

            else ->
                FooterViewHolder(ItemRecyclerFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == data.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.TRANSPARENT)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root), ItemTouchHelperViewHolder

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mainViewModel.swapServer(fromPosition, toPosition)
        if (fromPosition < data.size && toPosition < data.size) {
            Collections.swap(data, fromPosition, toPosition)
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {}

    override fun onItemDismiss(position: Int) {}
}
