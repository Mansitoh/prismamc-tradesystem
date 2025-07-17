package com.prismamc.trade.model;

import com.prismamc.trade.manager.TradeManager.TradeState;
import org.bukkit.inventory.ItemStack;
import org.bson.Document;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import java.io.IOException;

public class TradeDocument {
    private final long tradeId;
    private final UUID player1;
    private final UUID player2;
    private TradeState state;
    private final List<ItemStack> player1Items;
    private final List<ItemStack> player2Items;
    private final long timestamp;
    private boolean itemsSentToPlayer1;
    private boolean itemsSentToPlayer2;
    private boolean player1Accepted;
    private boolean player2Accepted;
    private static final int COMPRESSION_THRESHOLD = 1024; // 1KB

    public TradeDocument(long tradeId, UUID player1, UUID player2) {
        this.tradeId = tradeId;
        this.player1 = player1;
        this.player2 = player2;
        this.state = TradeState.PENDING;
        this.player1Items = new CopyOnWriteArrayList<>();
        this.player2Items = new CopyOnWriteArrayList<>();
        this.timestamp = System.currentTimeMillis();
        this.itemsSentToPlayer1 = false;
        this.itemsSentToPlayer2 = false;
        this.player1Accepted = false;
        this.player2Accepted = false;
    }

    public TradeDocument(Document doc) {
        this.tradeId = doc.getLong("tradeId");
        this.player1 = UUID.fromString(doc.getString("player1"));
        this.player2 = UUID.fromString(doc.getString("player2"));
        this.state = TradeState.valueOf(doc.getString("state"));
        this.timestamp = doc.getLong("timestamp");
        this.player1Items = deserializeItems(doc.getString("player1Items"));
        this.player2Items = deserializeItems(doc.getString("player2Items"));
        this.itemsSentToPlayer1 = doc.getBoolean("itemsSentToPlayer1", false);
        this.itemsSentToPlayer2 = doc.getBoolean("itemsSentToPlayer2", false);
        this.player1Accepted = doc.getBoolean("player1Accepted", false);
        this.player2Accepted = doc.getBoolean("player2Accepted", false);
    }

    public Document toDocument() {
        return new Document()
                .append("tradeId", tradeId)
                .append("player1", player1.toString())
                .append("player2", player2.toString())
                .append("state", state.name())
                .append("timestamp", timestamp)
                .append("player1Items", serializeItems(player1Items))
                .append("player2Items", serializeItems(player2Items))
                .append("itemsSentToPlayer1", itemsSentToPlayer1)
                .append("itemsSentToPlayer2", itemsSentToPlayer2)
                .append("player1Accepted", player1Accepted)
                .append("player2Accepted", player2Accepted);
    }

    public String serializeItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (ObjectOutputStream dataOutput = new ObjectOutputStream(outputStream)) {
                dataOutput.writeInt(items.size());
                for (ItemStack item : items) {
                    if (item != null) {
                        byte[] serializedItem = item.serializeAsBytes();
                        dataOutput.writeInt(serializedItem.length);
                        dataOutput.write(serializedItem);
                    }
                }
            }

            byte[] data = outputStream.toByteArray();
            if (data.length > COMPRESSION_THRESHOLD) {
                // Comprimir si excede el umbral
                ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
                try (GZIPOutputStream gzipStream = new GZIPOutputStream(compressedStream)) {
                    gzipStream.write(data);
                }
                return "GZIP:" + Base64.getEncoder().encodeToString(compressedStream.toByteArray());
            } else {
                return "RAW:" + Base64.getEncoder().encodeToString(data);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public List<ItemStack> deserializeItems(String base64) {
        List<ItemStack> items = new ArrayList<>();
        if (base64 == null || base64.isEmpty()) {
            return items;
        }

        try {
            String[] parts = base64.split(":", 2);
            if (parts.length != 2) {
                return items;
            }

            byte[] data = Base64.getDecoder().decode(parts[1]);
            if ("GZIP".equals(parts[0])) {
                ByteArrayOutputStream decompressedStream = new ByteArrayOutputStream();
                try (GZIPInputStream gzipStream = new GZIPInputStream(new ByteArrayInputStream(data))) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = gzipStream.read(buffer)) > 0) {
                        decompressedStream.write(buffer, 0, len);
                    }
                }
                data = decompressedStream.toByteArray();
            }

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                    ObjectInputStream dataInput = new ObjectInputStream(inputStream)) {

                int size = dataInput.readInt();
                for (int i = 0; i < size; i++) {
                    int itemLength = dataInput.readInt();
                    byte[] itemData = new byte[itemLength];
                    dataInput.readFully(itemData);
                    ItemStack item = ItemStack.deserializeBytes(itemData);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return items;
    }

    // Getters
    public long getTradeId() {
        return tradeId;
    }

    public UUID getPlayer1() {
        return player1;
    }

    public UUID getPlayer2() {
        return player2;
    }

    public TradeState getState() {
        return state;
    }

    public List<ItemStack> getPlayer1Items() {
        return new ArrayList<>(player1Items);
    }

    public List<ItemStack> getPlayer2Items() {
        return new ArrayList<>(player2Items);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean areItemsSentToPlayer1() {
        return itemsSentToPlayer1;
    }

    public boolean areItemsSentToPlayer2() {
        return itemsSentToPlayer2;
    }

    public boolean isPlayer1Accepted() {
        return player1Accepted;
    }

    public boolean isPlayer2Accepted() {
        return player2Accepted;
    }

    // Setters
    public void setState(TradeState state) {
        this.state = state;
    }

    public void setPlayer1Items(List<ItemStack> items) {
        player1Items.clear();
        if (items != null) {
            player1Items.addAll(items.stream()
                    .filter(item -> item != null && item.getType() != org.bukkit.Material.AIR)
                    .map(ItemStack::clone)
                    .toList());
        }
    }

    public void setPlayer2Items(List<ItemStack> items) {
        player2Items.clear();
        if (items != null) {
            player2Items.addAll(items.stream()
                    .filter(item -> item != null && item.getType() != org.bukkit.Material.AIR)
                    .map(ItemStack::clone)
                    .toList());
        }
    }

    public void setItemsSentToPlayer1(boolean sent) {
        this.itemsSentToPlayer1 = sent;
    }

    public void setItemsSentToPlayer2(boolean sent) {
        this.itemsSentToPlayer2 = sent;
    }

    public void setPlayer1Accepted(boolean accepted) {
        this.player1Accepted = accepted;
    }

    public void setPlayer2Accepted(boolean accepted) {
        this.player2Accepted = accepted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TradeDocument that = (TradeDocument) o;
        return tradeId == that.tradeId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(tradeId);
    }
}