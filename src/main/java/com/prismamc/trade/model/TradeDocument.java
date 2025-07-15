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
                .append("itemsSentToPlayer2", itemsSentToPlayer2);
    }

    public String serializeItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            boolean useCompression = false;
            byte[] serializedData;

            // Primera serialización para verificar tamaño
            try (ByteArrayOutputStream tempStream = new ByteArrayOutputStream();
                 ObjectOutputStream tempOutput = new ObjectOutputStream(tempStream)) {
                
                tempOutput.writeInt(items.size());
                for (ItemStack item : items) {
                    tempOutput.writeObject(item);
                }
                serializedData = tempStream.toByteArray();
                useCompression = serializedData.length > COMPRESSION_THRESHOLD;
            }

            // Serialización final con o sin compresión
            if (useCompression) {
                try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream);
                     ObjectOutputStream dataOutput = new ObjectOutputStream(gzipStream)) {
                    
                    dataOutput.writeInt(items.size());
                    for (ItemStack item : items) {
                        dataOutput.writeObject(item);
                    }
                }
                return "GZIP:" + Base64.getEncoder().encodeToString(outputStream.toByteArray());
            } else {
                return "RAW:" + Base64.getEncoder().encodeToString(serializedData);
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
            boolean isCompressed = "GZIP".equals(parts[0]);

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                 ObjectInputStream dataInput = isCompressed ?
                     new ObjectInputStream(new GZIPInputStream(inputStream)) :
                     new ObjectInputStream(inputStream)) {

                int size = dataInput.readInt();
                for (int i = 0; i < size; i++) {
                    ItemStack item = (ItemStack) dataInput.readObject();
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
    public long getTradeId() { return tradeId; }
    public UUID getPlayer1() { return player1; }
    public UUID getPlayer2() { return player2; }
    public TradeState getState() { return state; }
    public List<ItemStack> getPlayer1Items() { return new ArrayList<>(player1Items); }
    public List<ItemStack> getPlayer2Items() { return new ArrayList<>(player2Items); }
    public long getTimestamp() { return timestamp; }
    public boolean areItemsSentToPlayer1() { return itemsSentToPlayer1; }
    public boolean areItemsSentToPlayer2() { return itemsSentToPlayer2; }

    // Setters
    public void setState(TradeState state) { this.state = state; }
    
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
    
    public void setItemsSentToPlayer1(boolean sent) { this.itemsSentToPlayer1 = sent; }
    public void setItemsSentToPlayer2(boolean sent) { this.itemsSentToPlayer2 = sent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeDocument that = (TradeDocument) o;
        return tradeId == that.tradeId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(tradeId);
    }
}