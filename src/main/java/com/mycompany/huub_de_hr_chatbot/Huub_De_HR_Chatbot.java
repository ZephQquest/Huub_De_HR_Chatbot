package com.mycompany.huub_de_hr_chatbot;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.*;
import okhttp3.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class Huub_De_HR_Chatbot extends JFrame {

    // ==============================
    // CONFIGURATIE
    // ==============================

    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final OkHttpClient CLIENT = new OkHttpClient();

    private static final String PERSONEELSGIDS_VERSIE = "Personeelsgids versie 2024.1";

    private JPanel chatPanel;
    private JScrollPane scrollPane;
    private JTextField inputField;
    private Image backgroundImage;

    private final List<JSONObject> conversationHistory = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();

    // ==============================
    // DATASTRUCTUUR
    // ==============================

    static class Chunk {
        String text;
        List<Double> embedding;

        Chunk(String text, List<Double> embedding) {
            this.text = text;
            this.embedding = embedding;
        }
    }

    // ==============================
    // CONSTRUCTOR
    // ==============================

    public Huub_De_HR_Chatbot() throws Exception {

        setTitle("Huub – HR Chatbot (Verlof)");
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        backgroundImage = new ImageIcon("qquestlogoHoe gaa.png").getImage();

        setupChatPanel();
        setupInputPanel();

        setVisible(true);

        addBubble("Welkom! Ik ben Huub, jouw HR-assistent (domein: verlof).", false);
        addBubble("Gebruikte bron: " + PERSONEELSGIDS_VERSIE, false);

        loadGuide();
    }

    // ==============================
    // UI
    // ==============================

    private void setupChatPanel() {
        chatPanel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundImage, 0, 0, null);
            }
        };

        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setOpaque(false);

        scrollPane = new JScrollPane(chatPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
    }

    private void setupInputPanel() {
        inputField = new JTextField();
        JButton sendButton = new JButton("Verstuur");

        sendButton.setFocusPainted(false);
        sendButton.setBackground(new Color(0, 90, 160));
        sendButton.setForeground(Color.WHITE);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> send());
        inputField.addActionListener(e -> send());
    }

    // ==============================
    // CHAT
    // ==============================

    private void send() {
        String question = inputField.getText().trim();
        if (question.isEmpty()) return;

        addBubble(question, true);
        inputField.setText("");

        new Thread(() -> {
            try {
                String answer = ask(question);
                SwingUtilities.invokeLater(() -> addBubble(answer, false));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        addBubble("Er ging iets mis: " + ex.getMessage(), false));
            }
        }).start();
    }

    private void addBubble(String text, boolean user) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        JTextArea bubble = new JTextArea(text);
        bubble.setLineWrap(true);
        bubble.setWrapStyleWord(true);
        bubble.setEditable(false);
        bubble.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        bubble.setBorder(new EmptyBorder(14, 20, 14, 20));

        if (user) {
            bubble.setBackground(new Color(0, 90, 160));
            bubble.setForeground(Color.WHITE);
        } else {
            bubble.setBackground(new Color(255, 255, 255, 235));
            bubble.setForeground(Color.BLACK);
        }

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(8, 40, 8, 40));
        container.add(bubble, BorderLayout.CENTER);

        row.add(container, BorderLayout.CENTER);
        chatPanel.add(row);
        chatPanel.revalidate();

        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // ==============================
    // PDF + EMBEDDINGS
    // ==============================

    private void loadGuide() throws Exception {
        String pdfText = loadPdf("personeelsgids.pdf");
        List<String> parts = chunkText(pdfText, 400);

        for (String part : parts) {
            chunks.add(new Chunk(part, embed(part)));
        }
    }

    private static String loadPdf(String path) throws Exception {
        PDDocument doc = Loader.loadPDF(new File(path));
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(doc);
        doc.close();
        return text;
    }

    private static List<String> chunkText(String text, int size) {
        List<String> result = new ArrayList<>();
        String[] words = text.split("\\s+");

        for (int i = 0; i < words.length; i += size) {
            result.add(String.join(" ",
                    Arrays.copyOfRange(words, i, Math.min(words.length, i + size))));
        }
        return result;
    }

    private static List<Double> embed(String input) throws Exception {
        JSONObject body = new JSONObject()
                .put("model", "text-embedding-3-small")
                .put("input", input);

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/embeddings")
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                .build();

        Response response = CLIENT.newCall(request).execute();
        JSONArray arr = new JSONObject(response.body().string())
                .getJSONArray("data")
                .getJSONObject(0)
                .getJSONArray("embedding");

        List<Double> vector = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++)
            vector.add(arr.getDouble(i));

        return vector;
    }

    private double cosine(List<Double> a, List<Double> b) {
        double dot = 0, na = 0, nb = 0;

        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            na += a.get(i) * a.get(i);
            nb += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private List<String> search(String query) throws Exception {
        List<Double> qVec = embed(query);

        chunks.sort((a, b) -> Double.compare(
                cosine(b.embedding, qVec),
                cosine(a.embedding, qVec)
        ));

        List<String> top = new ArrayList<>();
        for (int i = 0; i < Math.min(4, chunks.size()); i++)
            top.add(chunks.get(i).text);

        return top;
    }

    // ==============================
    // OPENAI CHAT
    // ==============================

    private String ask(String question) throws Exception {

        List<String> context = search(question);

        String systemPrompt =
            "Je bent Huub, een professionele HR-assistent gespecialiseerd in het domein VERLOF. " +
            "SCOPE: " +
            "Je beantwoordt uitsluitend vragen over verlof. " +
            "Dit omvat bijvoorbeeld vakantieverlof, bijzonder verlof, ouderschapsverlof, ziekteverlof en het opnemen van vrije dagen. " +
            "Als een vraag niet over verlof gaat, geef je netjes aan dat je binnen deze sprint alleen verlofvragen ondersteunt. " +
            "AGENT 1 – PERSONEELSGIDS: " +
            "Controleer altijd eerst of het antwoord in het onderdeel verlof van de personeelsgids staat. " +
            "Gebruik uitsluitend informatie uit de personeelsgids en verwijs altijd naar hoofdstuk en pagina. " +
            "Gebruik primair het onderdeel verlof uit de personeelsgids als hoofdbron. " +
            "BELANGRIJK (USER STORIES): " +
            "Als het antwoord niet in de personeelsgids staat of onvoldoende duidelijk is, zeg je expliciet dat je het antwoord niet uit de personeelsgids kunt halen. " + // user story 24
            "Adviseer in dat geval de medewerker om contact op te nemen met zijn of haar leidinggevende. " + // user story 24
            "Je verzint nooit informatie. " +
            "Je geeft geen waardeoordelen, meningen of kwalificaties zoals 'goed', 'slecht', 'verstandig' of 'aan te raden'. " + // user story 25
            "Je antwoordt uitsluitend feitelijk en neutraal op basis van de personeelsgids. " + // user story 25
            "GESPREKSGEHEUGEN: " +
            "Je onthoudt eerdere vragen en antwoorden binnen dit gesprek en gebruikt deze als context. " + // deze werkt nog niet..
            "REGELS: " +
            "Je vraagt om extra informatie als iets onduidelijk is. " +
            "Je sluit elk inhoudelijk antwoord af met een korte disclaimer dat het antwoord mogelijk onvolledig of contextafhankelijk is. " +
            "Je vraagt of de gebruiker tevreden is met het antwoord.";

        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", systemPrompt));

        for (JSONObject m : conversationHistory)
            messages.put(m);

        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", "PERSOONNELSGIDS:\n" +
                        String.join("\n", context) +
                        "\n\nVRAAG:\n" + question));

        JSONObject body = new JSONObject()
                .put("model", "gpt-4o-mini")
                .put("messages", messages);

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                .build();

        Response response = CLIENT.newCall(request).execute();

        String answer = new JSONObject(response.body().string())
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        conversationHistory.add(new JSONObject().put("role", "user").put("content", question));
        conversationHistory.add(new JSONObject().put("role", "assistant").put("content", answer));

        if (conversationHistory.size() > 12)
            conversationHistory.subList(0,
                    conversationHistory.size() - 12).clear();

        return answer;
    }

    // ==============================

    public static void main(String[] args) throws Exception {
        new Huub_De_HR_Chatbot();
    }
}
