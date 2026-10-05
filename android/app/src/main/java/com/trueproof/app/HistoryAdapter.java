package com.trueproof.app;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(HistoryItem item);
    }

    private final List<HistoryItem> items;
    private final OnItemClickListener listener;

    public HistoryAdapter(List<HistoryItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);

        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position) {

        HistoryItem item = items.get(position);

        holder.title.setText(
                (item.amount != null
                        ? "₹" + item.amount
                        : "Amount unknown")
                        + " · " +
                        (item.upiId != null
                                ? item.upiId
                                : "Unknown UPI")
        );

        holder.subtitle.setText(
                DateFormat.format(
                        "dd MMM, hh:mm a",
                        item.timestamp
                )
        );

        String verdict = item.verdict;

        if (verdict == null || verdict.trim().isEmpty()) {
            verdict = "UNKNOWN";
        }

        holder.badge.setText(verdict);

        int colorRes;

        switch (verdict) {
            case "ORIGINAL":
                colorRes = R.color.verified;
                break;

            case "EDITED":
                colorRes = R.color.fake;
                break;

            default:
                colorRes = R.color.text_dim;
                break;
        }

        holder.badge.setTextColor(
                ContextCompat.getColor(
                        holder.itemView.getContext(),
                        colorRes
                )
        );

        holder.itemView.setOnClickListener(
                v -> listener.onItemClick(item)
        );
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView title;
        TextView subtitle;
        TextView badge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            title = itemView.findViewById(R.id.tvTitle);
            subtitle = itemView.findViewById(R.id.tvSubtitle);
            badge = itemView.findViewById(R.id.tvBadge);
        }
    }
}