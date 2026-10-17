import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Main {
    private static final Color BACKGROUND = new Color(245, 248, 250);
    private static final Color NAVY = new Color(20, 48, 68);
    private static final Color TEAL = new Color(0, 125, 125);
    private static final Color TEXT = new Color(37, 55, 68);
    private static final Font BODY_FONT = new Font("SansSerif", Font.PLAIN, 14);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final JFrame frame = new JFrame("MedEstudio | Asistente de aprendizaje");
    private final JTextField searchField = new JTextField();
    private final JButton searchButton = new JButton("Buscar");
    private final JComboBox<String> languageSelector = new JComboBox<>(new String[]{"Español", "English"});
    private final JLabel subtitleLabel = new JLabel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JPanel resultsPanel = new JPanel();
    private final JLabel imageLabel = new JLabel("Las imágenes del tema aparecerán aquí", SwingConstants.CENTER);
    private final JLabel imageTitleLabel = new JLabel("Apoyo visual");
    private final JLabel imageCaption = new JLabel(" ");
    private final JButton imageSourceButton = new JButton("Ver imagen y licencia");
    private final JButton previousImageButton = new JButton("◀");
    private final JButton nextImageButton = new JButton("▶");
    private final JButton[] quickSearchButtons = new JButton[12];
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextArea chatTranscript = new JTextArea();
    private final JTextArea chatInput = new JTextArea(3, 30);
    private final JButton sendChatButton = new JButton();
    private final JButton configureKeyButton = new JButton();
    private final JButton newChatButton = new JButton();
    private final JLabel chatStatusLabel = new JLabel(" ");
    private final JLabel chatIntroLabel = new JLabel();
    private final JLabel chatPrivacyLabel = new JLabel();
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private String geminiApiKey = System.getenv("GEMINI_API_KEY");
    private String aiProvider = geminiApiKey == null || geminiApiKey.isBlank() ? "ollama" : "gemini";
    private String ollamaModel = "qwen2.5:3b";
    private List<CommonsImage> currentImages = List.of();
    private int currentImageIndex;
    private String lastQuery = "";

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> new Main().show());
    }

    private void show() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Swing's default look and feel is an acceptable fallback.
        }

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(850, 650));
        frame.setSize(1060, 760);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BACKGROUND);
        frame.setLayout(new BorderLayout(0, 0));
        frame.add(buildHeader(), BorderLayout.NORTH);
        frame.add(buildMainContent(), BorderLayout.CENTER);
        languageSelector.addActionListener(event -> changeLanguage());
        updateLanguage();
        showWelcome();
        frame.setVisible(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(20, 30, 18, 30));
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("MedEstudio");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 27));
        subtitleLabel.setForeground(new Color(208, 226, 233));
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        languageSelector.setToolTipText("Idioma / Language");
        titleRow.add(languageSelector, BorderLayout.EAST);

        JPanel searchRow = new JPanel(new BorderLayout(10, 0));
        searchRow.setOpaque(false);
        searchRow.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        searchField.setFont(BODY_FONT);
        searchField.addActionListener(this::onSearch);
        searchButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        searchButton.setBackground(new Color(68, 195, 176));
        searchButton.setForeground(NAVY);
        searchButton.setFocusPainted(false);
        searchButton.addActionListener(this::onSearch);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);

        header.add(titleRow);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitleLabel);
        header.add(searchRow);
        return header;
    }

    private JTabbedPane buildMainContent() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));

        JPanel quickSearchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quickSearchPanel.setBackground(BACKGROUND);
        String[] quickTopics = {
                "Diabetes", "Anemia", "Asma", "Hipertensión",
                "Migraña", "Neumonía", "Artritis", "Gastritis",
                "Depresión", "Infección urinaria", "COVID-19", "Infarto"
        };
        for (int i = 0; i < quickTopics.length; i++) {
            int topicIndex = i;
            JButton button = new JButton(quickTopics[i]);
            quickSearchButtons[i] = button;
            button.setFocusPainted(false);
            button.setFont(new Font("SansSerif", Font.PLAIN, 12));
            button.setForeground(TEAL);
            button.addActionListener(event -> search(quickTopic(topicIndex)));
            quickSearchPanel.add(button);
        }

        resultsPanel.setBackground(Color.WHITE);
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JScrollPane resultsScroll = new JScrollPane(resultsPanel);
        resultsScroll.setBorder(BorderFactory.createLineBorder(new Color(223, 231, 235)));
        resultsScroll.getViewport().setBackground(Color.WHITE);

        JPanel imagePanel = new JPanel();
        imagePanel.setBackground(Color.WHITE);
        imagePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(223, 231, 235)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        imagePanel.setLayout(new BoxLayout(imagePanel, BoxLayout.Y_AXIS));
        imageTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        imageTitleLabel.setForeground(NAVY);
        imageTitleLabel.setAlignmentX(0.0f);
        imageLabel.setPreferredSize(new Dimension(310, 235));
        imageLabel.setMinimumSize(new Dimension(240, 180));
        imageLabel.setMaximumSize(new Dimension(360, 270));
        imageLabel.setAlignmentX(0.5f);
        imageLabel.setForeground(new Color(115, 132, 143));
        imageCaption.setFont(new Font("SansSerif", Font.PLAIN, 12));
        imageCaption.setForeground(TEXT);
        imageCaption.setAlignmentX(0.0f);
        imageSourceButton.setAlignmentX(0.0f);
        imageSourceButton.setFocusPainted(false);
        imageSourceButton.setEnabled(false);
        imageSourceButton.addActionListener(event -> {
            if (!currentImages.isEmpty()) {
                openUrl(currentImages.get(currentImageIndex).sourceUrl());
            }
        });
        previousImageButton.setFocusPainted(false);
        nextImageButton.setFocusPainted(false);
        previousImageButton.setEnabled(false);
        nextImageButton.setEnabled(false);
        previousImageButton.addActionListener(event -> showImage(currentImageIndex - 1));
        nextImageButton.addActionListener(event -> showImage(currentImageIndex + 1));
        JPanel imageNavigation = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        imageNavigation.setOpaque(false);
        imageNavigation.add(previousImageButton);
        imageNavigation.add(nextImageButton);

        imagePanel.add(imageTitleLabel);
        imagePanel.add(Box.createVerticalStrut(12));
        imagePanel.add(imageLabel);
        imagePanel.add(imageNavigation);
        imagePanel.add(Box.createVerticalStrut(10));
        imagePanel.add(imageCaption);
        imagePanel.add(Box.createVerticalStrut(8));
        imagePanel.add(imageSourceButton);

        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        body.add(resultsScroll, BorderLayout.CENTER);
        body.add(imagePanel, BorderLayout.EAST);
        imagePanel.setPreferredSize(new Dimension(340, 380));

        statusLabel.setForeground(TEAL);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(quickSearchPanel, BorderLayout.NORTH);
        center.add(body, BorderLayout.CENTER);
        center.add(statusLabel, BorderLayout.SOUTH);
        content.add(center, BorderLayout.CENTER);
        tabs.addTab("Buscar", content);
        tabs.addTab("Chat con IA", buildChatPanel());
        return tabs;
    }

    private JPanel buildChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 6, 4, 6));

        JPanel top = new JPanel(new BorderLayout(8, 4));
        top.setOpaque(false);
        chatIntroLabel.setForeground(NAVY);
        top.add(chatIntroLabel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        configureKeyButton.setText(tr("Configurar IA", "Configure AI"));
        newChatButton.setText(tr("Nuevo chat", "New chat"));
        configureKeyButton.addActionListener(event -> configureAi());
        newChatButton.addActionListener(event -> clearChat());
        actions.add(configureKeyButton);
        actions.add(newChatButton);
        top.add(actions, BorderLayout.EAST);

        chatTranscript.setEditable(false);
        chatTranscript.setLineWrap(true);
        chatTranscript.setWrapStyleWord(true);
        chatTranscript.setFont(BODY_FONT);
        chatTranscript.setForeground(TEXT);
        chatTranscript.setBackground(Color.WHITE);
        chatTranscript.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JScrollPane transcriptScroll = new JScrollPane(chatTranscript);
        transcriptScroll.setBorder(BorderFactory.createLineBorder(new Color(223, 231, 235)));

        chatInput.setLineWrap(true);
        chatInput.setWrapStyleWord(true);
        chatInput.setFont(BODY_FONT);
        chatInput.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        chatInput.setToolTipText(tr("Escribe tu pregunta y pulsa Enviar", "Type your question and press Send"));
        JScrollPane inputScroll = new JScrollPane(chatInput);
        inputScroll.setPreferredSize(new Dimension(100, 82));

        sendChatButton.setText(tr("Enviar", "Send"));
        sendChatButton.setBackground(new Color(68, 195, 176));
        sendChatButton.setForeground(NAVY);
        sendChatButton.setFocusPainted(false);
        sendChatButton.addActionListener(event -> sendChatMessage());
        chatInput.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("control ENTER"), "send-chat");
        chatInput.getActionMap().put("send-chat", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                sendChatMessage();
            }
        });

        JPanel composer = new JPanel(new BorderLayout(8, 0));
        composer.setOpaque(false);
        composer.add(inputScroll, BorderLayout.CENTER);
        composer.add(sendChatButton, BorderLayout.EAST);

        chatStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatStatusLabel.setForeground(TEAL);
        chatPrivacyLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        chatPrivacyLabel.setForeground(new Color(90, 104, 115));

        JPanel bottom = new JPanel(new BorderLayout(0, 5));
        bottom.setOpaque(false);
        bottom.add(composer, BorderLayout.CENTER);
        JPanel notices = new JPanel(new GridLayout(2, 1, 0, 3));
        notices.setOpaque(false);
        notices.add(chatStatusLabel);
        notices.add(chatPrivacyLabel);
        bottom.add(notices, BorderLayout.SOUTH);

        panel.add(top, BorderLayout.NORTH);
        panel.add(transcriptScroll, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        showChatWelcome();
        return panel;
    }

    private void showWelcome() {
        resultsPanel.removeAll();
        imageLabel.setIcon(null);
        imageLabel.setText(tr("Las imágenes del tema aparecerán aquí", "Topic images will appear here"));
        imageCaption.setText(" ");
        imageSourceButton.setEnabled(false);
        statusLabel.setText(" ");
        addHeading(tr("Bienvenido/a a MedEstudio", "Welcome to MedEstudio"));
        addParagraph(tr("Busca una enfermedad, síntoma o concepto médico. Consulta MedlinePlus y encuentra "
                        + "una imagen relacionada en Wikimedia Commons.",
                "Search for a disease, symptom, or medical concept. Get information from MedlinePlus and find "
                        + "a related image on Wikimedia Commons."));
        addParagraph(tr("En español, se intenta traducir el resumen automáticamente. Si el servicio de traducción "
                        + "no está disponible, se muestra el texto original y se indica. Verifica siempre los datos "
                        + "en el artículo original.",
                "In English, summaries are shown from the original source. Always verify information in the "
                        + "original article; this tool does not replace professional review."));
        addHeading(tr("Para empezar", "Getting started"));
        addParagraph(tr("Busca cualquier enfermedad o síntoma, o elige entre diabetes, anemia, asma, hipertensión, "
                        + "migraña, neumonía, artritis, gastritis, depresión, infección urinaria, COVID-19 e infarto.",
                "Search for any disease or symptom, or choose diabetes, anemia, asthma, high blood pressure, "
                        + "migraine, pneumonia, arthritis, gastritis, depression, urinary infection, COVID-19, or heart attack."));
        addHeading(tr("Recursos de consulta", "Reference resources"));
        addLinkButton(tr("MedlinePlus en español", "MedlinePlus"), tr(
                "https://medlineplus.gov/spanish/", "https://medlineplus.gov/"));
        addLinkButton(tr("Organización Mundial de la Salud", "World Health Organization"),
                tr("https://www.who.int/es", "https://www.who.int/"));
        addLinkButton(tr("Manual MSD", "MSD Manual"),
                tr("https://www.msdmanuals.com/es", "https://www.msdmanuals.com/"));
        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private void showChatWelcome() {
        String provider = chatAssistantName();
        chatTranscript.setText(tr(
                "MedEstudio con " + provider + "\nPregunta sobre enfermedades, anatomía, fisiología, farmacología o conceptos clínicos para estudiar.\n\n"
                        + "Las respuestas de IA pueden contener errores. No son diagnóstico ni tratamiento. "
                        + (aiProvider.equals("ollama")
                        ? "El modelo local no consulta la web; verifica la información en MedlinePlus u otras fuentes médicas."
                        : "Verifica las fuentes citadas y consulta a un profesional."),
                "MedEstudio with " + provider + "\nAsk about diseases, anatomy, physiology, pharmacology, or clinical concepts for study.\n\n"
                        + "AI responses may contain errors. They are not diagnosis or treatment. "
                        + (aiProvider.equals("ollama")
                        ? "The local model does not search the web; verify information with MedlinePlus or other medical sources."
                        : "Verify cited sources and consult a healthcare professional.")));
        chatTranscript.setCaretPosition(0);
        chatStatusLabel.setText(aiProvider.equals("ollama")
                ? tr("IA local gratuita seleccionada: requiere Ollama y el modelo " + ollamaModel + ".",
                        "Free local AI selected: Ollama and model " + ollamaModel + " are required.")
                : geminiApiKey == null || geminiApiKey.isBlank()
                ? tr("Configura tu clave de Gemini para empezar.", "Set your Gemini API key to get started.")
                : tr("Gemini listo.", "Gemini is ready."));
    }

    private void configureAi() {
        String previousProvider = aiProvider;
        String[] options = {
                tr("Ollama local (gratis, sin clave)", "Local Ollama (free, no key)"),
                tr("Gemini (requiere clave API)", "Gemini (API key required)")
        };
        int selected = javax.swing.JOptionPane.showOptionDialog(frame,
                tr("Elige cómo ejecutar el chat. Ollama usa un modelo descargado en tu computadora; Gemini requiere una clave y puede tener límites de uso.",
                        "Choose how to run the chat. Ollama uses a model downloaded to your computer; Gemini requires an API key and may have usage limits."),
                tr("Configurar IA", "Configure AI"),
                javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE,
                null, options, aiProvider.equals("ollama") ? options[0] : options[1]);
        if (selected == 0) {
            aiProvider = "ollama";
            showOllamaSetup();
        } else if (selected == 1) {
            configureGeminiKey();
        }
        if (!previousProvider.equals(aiProvider)) {
            chatHistory.clear();
            showChatWelcome();
        }
        updateLanguage();
    }

    private void showOllamaSetup() {
        JPanel prompt = new JPanel(new BorderLayout(0, 8));
        prompt.add(new JLabel("<html>" + tr(
                "Ollama es gratuito y ejecuta la IA en esta computadora, sin clave API. Instálalo y luego ejecuta este comando en PowerShell:<br><br>",
                "Ollama is free and runs the AI on this computer without an API key. Install it, then run this command in PowerShell:<br><br>")
                + "<b>ollama run " + ollamaModel + "</b><br><br>"
                + tr("La primera vez descargará el modelo; después deja Ollama abierto mientras usas el chat. "
                        + "El modelo no tiene búsqueda web en vivo. La velocidad depende de tu computadora.",
                        "The first run downloads the model; keep Ollama open while using the chat. "
                                + "The model has no live web search. Speed depends on your computer.")
                + "</html>"), BorderLayout.CENTER);
        JButton downloadButton = new JButton(tr("Descargar Ollama", "Download Ollama"));
        downloadButton.addActionListener(event -> openUrl("https://ollama.com/download/windows"));
        prompt.add(downloadButton, BorderLayout.SOUTH);
        javax.swing.JOptionPane.showMessageDialog(frame, prompt, tr("IA local gratuita", "Free local AI"),
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
        chatStatusLabel.setText(tr("Ollama seleccionado. Instálalo y descarga el modelo para empezar.",
                "Ollama selected. Install it and download the model to get started."));
    }

    private void configureGeminiKey() {
        JPasswordField keyField = new JPasswordField(32);
        JPanel prompt = new JPanel(new BorderLayout(0, 8));
        prompt.add(new JLabel(tr("Pega tu clave de Gemini API. Solo se guardará mientras la aplicación esté abierta.",
                "Paste your Gemini API key. It will only be kept while the application is open.")),
                BorderLayout.NORTH);
        prompt.add(keyField, BorderLayout.CENTER);
        JButton getKeyButton = new JButton(tr("Obtener clave en Google AI Studio", "Get a key from Google AI Studio"));
        getKeyButton.addActionListener(event -> openUrl("https://aistudio.google.com/apikey"));
        prompt.add(getKeyButton, BorderLayout.SOUTH);
        int result = javax.swing.JOptionPane.showConfirmDialog(frame, prompt,
                tr("Configurar Gemini", "Configure Gemini"),
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        char[] enteredKey = keyField.getPassword();
        if (result == javax.swing.JOptionPane.OK_OPTION && enteredKey.length > 0) {
            geminiApiKey = new String(enteredKey).trim();
            aiProvider = "gemini";
            chatStatusLabel.setText(tr("Clave cargada en memoria.", "API key loaded in memory."));
        }
        java.util.Arrays.fill(enteredKey, '\0');
        keyField.setText("");
        if (result == javax.swing.JOptionPane.OK_OPTION && (geminiApiKey == null || geminiApiKey.isBlank())) {
            chatStatusLabel.setText(tr("No se ingresó una clave.", "No API key was entered."));
        }
    }

    private void clearChat() {
        chatHistory.clear();
        showChatWelcome();
        if (aiProvider.equals("gemini") && (geminiApiKey == null || geminiApiKey.isBlank())) {
            chatStatusLabel.setText(tr("Configura tu clave de Gemini para empezar.",
                    "Set your Gemini API key to get started."));
        } else if (aiProvider.equals("gemini")) {
            chatStatusLabel.setText(tr("Chat nuevo listo.", "New chat ready."));
        }
    }

    private void sendChatMessage() {
        String userText = chatInput.getText().trim();
        if (userText.isEmpty()) {
            chatStatusLabel.setText(tr("Escribe una pregunta primero.", "Type a question first."));
            return;
        }
        if (userText.length() > 4000) {
            chatStatusLabel.setText(tr("La pregunta es demasiado larga (máximo 4000 caracteres).",
                    "The question is too long (maximum 4000 characters)."));
            return;
        }
        if (aiProvider.equals("gemini") && (geminiApiKey == null || geminiApiKey.isBlank())) {
            configureGeminiKey();
            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                return;
            }
        }

        boolean spanishResponse = isSpanish();
        String provider = aiProvider;
        String apiKey = geminiApiKey;
        String model = ollamaModel;
        chatInput.setText("");
        appendChatMessage(tr("Tú", "You"), userText);
        setChatBusy(true);
        chatStatusLabel.setText(tr("Gemini está preparando una respuesta...", "Gemini is preparing a response..."));

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return provider.equals("ollama")
                        ? askOllama(model, userText, spanishResponse)
                        : askGemini(apiKey, userText, spanishResponse);
            }

            @Override
            protected void done() {
                setChatBusy(false);
                try {
                    String answer = get();
                    chatHistory.add(new ChatMessage("user", userText));
                    chatHistory.add(new ChatMessage("model", answer));
                    while (chatHistory.size() > 20) {
                        chatHistory.remove(0);
                    }
                    appendChatMessage(provider.equals("ollama") ? "Ollama" : "Gemini", answer);
                    chatStatusLabel.setText(provider.equals("ollama")
                            ? tr("Respuesta generada localmente por Ollama.", "Response generated locally by Ollama.")
                            : tr("Respuesta generada con búsqueda web cuando fue pertinente.",
                                    "Response generated with web search when relevant."));
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    appendChatMessage(tr("Error", "Error"), safeMessage(cause));
                    chatStatusLabel.setText(provider.equals("ollama")
                            ? tr("No se pudo obtener respuesta de Ollama.", "Could not get an Ollama response.")
                            : tr("No se pudo obtener respuesta de Gemini.", "Could not get a Gemini response."));
                }
            }
        }.execute();
    }

    private String askOllama(String model, String userText, boolean spanishResponse) throws Exception {
        String systemPrompt = "You are MedEstudio, an AI assistant for medical education and study. "
                + "Explain clearly and conversationally. Distinguish established facts from uncertainty. "
                + "Never diagnose or prescribe for an individual, and do not request patient identifiers. "
                + "Do not claim to have searched the web or invent citations and URLs. "
                + "For current information, recommend checking MedlinePlus, WHO, or another trusted medical source. "
                + "For personal symptoms recommend consulting a licensed healthcare professional; for emergency "
                + "symptoms recommend local emergency services. "
                + (spanishResponse ? "Always answer in Spanish." : "Always answer in English.");
        StringBuilder messages = new StringBuilder("[{\"role\":\"system\",\"content\":\"")
                .append(jsonEscape(systemPrompt)).append("\"}");
        int start = Math.max(0, chatHistory.size() - 10);
        for (int i = start; i < chatHistory.size(); i++) {
            ChatMessage message = chatHistory.get(i);
            String role = message.role().equals("user") ? "user" : "assistant";
            messages.append(",{\"role\":\"").append(role).append("\",\"content\":\"")
                    .append(jsonEscape(message.text())).append("\"}");
        }
        messages.append(",{\"role\":\"user\",\"content\":\"")
                .append(jsonEscape(userText)).append("\"}]");
        String requestBody = "{\"model\":\"" + jsonEscape(model) + "\",\"stream\":false,\"messages\":"
                + messages + "}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/chat"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(spanishResponse
                    ? "No se pudo conectar con Ollama. Instálalo, ábrelo y ejecuta: ollama run " + model
                    : "Could not connect to Ollama. Install and open it, then run: ollama run " + model, exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String apiMessage = jsonField(response.body(), "error");
            if (apiMessage.toLowerCase().contains("not found")) {
                apiMessage = spanishResponse
                        ? "No está descargado el modelo. Ejecuta en PowerShell: ollama run " + model
                        : "The model is not downloaded. Run in PowerShell: ollama run " + model;
            }
            throw new IllegalStateException(apiMessage.isBlank()
                    ? "Ollama devolvió HTTP " + response.statusCode() + "."
                    : "Ollama: " + apiMessage);
        }
        String answer = jsonField(response.body(), "content");
        if (answer.isBlank()) {
            throw new IllegalStateException("Ollama no devolvió texto.");
        }
        return answer;
    }

    private String chatAssistantName() {
        return aiProvider.equals("ollama") ? "Ollama (" + ollamaModel + ")" : "Gemini";
    }

    private String askGemini(String apiKey, String userText, boolean spanishResponse) throws Exception {
        StringBuilder input = new StringBuilder();
        input.append("You are MedEstudio, an AI assistant for medical education and study. ")
                .append("Explain clearly and conversationally. Distinguish established facts from uncertainty. ")
                .append("Never diagnose or prescribe for an individual, and do not request patient identifiers. ")
                .append("Use Google Search for current or disease-specific questions. Cite trustworthy sources ")
                .append("with their full URLs, prioritizing government health agencies, universities, and peer-reviewed ")
                .append("medical sources. Never invent citations or claim to have searched if you did not. For personal ")
                .append("symptoms recommend consulting a licensed healthcare professional; for emergency symptoms recommend ")
                .append("local emergency services. ");
        input.append(spanishResponse ? "Always answer in Spanish.\n\n" : "Always answer in English.\n\n");
        input.append("Conversation so far:\n");
        int start = Math.max(0, chatHistory.size() - 10);
        for (int i = start; i < chatHistory.size(); i++) {
            ChatMessage message = chatHistory.get(i);
            input.append(message.role().equals("user") ? "User: " : "Assistant: ")
                    .append(message.text()).append('\n');
        }
        input.append("User: ").append(userText).append("\nAssistant:");

        String requestBody = "{\"model\":\"gemini-3.8-flash\","
                + "\"input\":\"" + jsonEscape(input.toString()) + "\","
                + "\"tools\":[{\"type\":\"google_search\"}],"
                + "\"store\":false}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "https://generativelanguage.googleapis.com/v1beta/interactions"))
                .timeout(Duration.ofSeconds(60))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String apiMessage = jsonField(response.body(), "message");
            throw new IllegalStateException(apiMessage.isBlank()
                    ? "Gemini devolvió HTTP " + response.statusCode() + "."
                    : "Gemini: " + apiMessage);
        }
        String answer = extractGeminiText(response.body());
        if (answer.isBlank()) {
            throw new IllegalStateException("Gemini no devolvió texto. Puede que la respuesta haya sido bloqueada.");
        }
        String citations = extractGeminiCitations(response.body());
        return citations.isBlank() ? answer : answer + "\n\n" + tr("Fuentes:", "Sources:") + "\n" + citations;
    }

    private void appendChatMessage(String speaker, String message) {
        if (chatTranscript.getDocument().getLength() > 0) {
            chatTranscript.append("\n\n");
        }
        chatTranscript.append(speaker + ":\n" + message);
        chatTranscript.setCaretPosition(chatTranscript.getDocument().getLength());
    }

    private void setChatBusy(boolean busy) {
        sendChatButton.setEnabled(!busy);
        newChatButton.setEnabled(!busy);
        configureKeyButton.setEnabled(!busy);
        chatInput.setEnabled(!busy);
        languageSelector.setEnabled(!busy);
    }

    private void onSearch(ActionEvent event) {
        search(searchField.getText());
    }

    private void search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            statusLabel.setText(tr("Escribe un tema para comenzar la búsqueda.",
                    "Enter a topic to start searching."));
            searchField.requestFocusInWindow();
            return;
        }

        searchField.setText(query);
        lastQuery = query;
        boolean spanishSearch = isSpanish();
        setSearching(true);
        currentImages = List.of();
        statusLabel.setText(tr("Buscando información e imágenes...", "Searching information and images..."));
        imageLabel.setIcon(null);
        imageLabel.setText(tr("Buscando imagen...", "Loading image..."));
        imageCaption.setText(" ");
        imageSourceButton.setEnabled(false);
        previousImageButton.setEnabled(false);
        nextImageButton.setEnabled(false);

        new SwingWorker<SearchBundle, Void>() {
            @Override
            protected SearchBundle doInBackground() throws Exception {
                List<MedicalResult> medicalResults = searchMedlinePlus(query, spanishSearch);
                boolean translationFailed = false;
                if (spanishSearch) {
                    List<MedicalResult> translatedResults = new ArrayList<>();
                    for (MedicalResult result : medicalResults) {
                        try {
                            SpanishArticle article = fetchSpanishArticle(result);
                            translatedResults.add(new MedicalResult(article.title(), article.summary(),
                                    spanishArticleUrl(result.url())));
                        } catch (Exception exception) {
                            String title = result.title();
                            String summary = limitText(result.summary(), 2600);
                            try {
                                title = translate(title);
                                summary = translate(summary);
                            } catch (Exception ignored) {
                                // Keep the verified MedlinePlus source text if translation is unavailable.
                                translationFailed = true;
                            }
                            translatedResults.add(new MedicalResult(title, summary, result.url()));
                        }
                    }
                    medicalResults = translatedResults;
                }
                List<CommonsImage> images = List.of();
                String imageError = "";
                try {
                    images = searchCommonsImages(query);
                } catch (Exception exception) {
                    imageError = (spanishSearch ? "No se pudieron cargar imágenes: "
                            : "Could not load images: ") + safeMessage(exception);
                }
                return new SearchBundle(medicalResults, images, imageError, translationFailed);
            }

            @Override
            protected void done() {
                setSearching(false);
                try {
                    SearchBundle bundle = get();
                    displayResults(query, bundle);
                    String status = bundle.results().isEmpty()
                            ? tr("MedlinePlus no encontró resultados; revisa las sugerencias y fuentes.",
                                    "MedlinePlus found no results; check suggestions and sources.")
                            : tr("Información de MedlinePlus · Imágenes de Wikimedia Commons.",
                                    "Information from MedlinePlus · Images from Wikimedia Commons.");
                    if (bundle.translationFailed()) {
                        status += tr(" Falló la traducción automática; algunos textos pueden estar en inglés.",
                                " Automatic translation failed; some text may remain in English.");
                    }
                    if (!bundle.imageError().isBlank()) {
                        status += " " + bundle.imageError();
                    }
                    statusLabel.setText(status);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    displayError(query, cause);
                }
            }
        }.execute();
    }

    private void displayResults(String query, SearchBundle bundle) {
        resultsPanel.removeAll();
        addHeading(tr("Resultados para: ", "Results for: ") + query);

        if (bundle.results().isEmpty()) {
            addParagraph(tr("No se encontraron artículos en MedlinePlus para esta búsqueda. "
                            + "Prueba con otro término o consulta directamente su sitio web.",
                    "MedlinePlus found no articles for this search. Try another term or search its website."));
            addLinkButton(tr("Buscar en MedlinePlus", "Search MedlinePlus"),
                    medlinePlusSearchUrl(query, isSpanish()));
        } else {
            for (MedicalResult result : bundle.results()) {
                addHeading(result.title());
                if (!result.summary().isBlank()) {
                    addHeading(tr("Descripción y datos reportados por MedlinePlus",
                            "Description and details reported by MedlinePlus"));
                    addParagraph(result.summary());
                } else {
                    addParagraph(tr("MedlinePlus no proporcionó un resumen para este resultado. "
                                    + "Abre el artículo para consultar los detalles.",
                            "MedlinePlus did not provide a summary. Open the article for details."));
                }
                addLinkButton(tr("Leer artículo en MedlinePlus", "Read article on MedlinePlus"),
                        result.url());
                addSeparator();
            }
        }

        currentImages = bundle.images();
        currentImageIndex = 0;
        if (currentImages.isEmpty()) {
            imageLabel.setIcon(null);
            imageLabel.setText(bundle.imageError().isBlank()
                    ? tr("No se encontró una imagen para este tema.", "No image was found for this topic.")
                    : tr("Imagen no disponible.", "Image unavailable."));
            imageCaption.setText(bundle.imageError().isBlank()
                    ? tr("Puedes probar con un término más general.", "Try a broader search term.")
                    : escapeHtml(bundle.imageError()));
            imageSourceButton.setEnabled(false);
            previousImageButton.setEnabled(false);
            nextImageButton.setEnabled(false);
        } else {
            showImage(0);
        }

        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private void displayError(String query, Throwable error) {
        resultsPanel.removeAll();
        addHeading(tr("No se pudo completar la búsqueda", "Search could not be completed"));
        addParagraph(tr("No fue posible conectar con las fuentes médicas o de traducción. "
                        + "Comprueba tu conexión e inténtalo de nuevo. Detalle: ",
                "Could not connect to the medical or translation sources. Check your connection and try again. "
                        + "Details: ") + safeMessage(error));
        addLinkButton(tr("Abrir MedlinePlus en español", "Open MedlinePlus"),
                tr("https://medlineplus.gov/spanish/", "https://medlineplus.gov/"));
        currentImages = List.of();
        imageLabel.setIcon(null);
        imageLabel.setText(tr("Imagen no disponible", "Image unavailable"));
        imageCaption.setText(tr("La búsqueda de «", "Searching for “") + escapeHtml(query)
                + tr("» requiere conexión a internet.", "” requires an internet connection."));
        imageSourceButton.setEnabled(false);
        previousImageButton.setEnabled(false);
        nextImageButton.setEnabled(false);
        resultsPanel.revalidate();
        resultsPanel.repaint();
        statusLabel.setText(tr("La consulta en línea falló; no se mostraron datos inventados.",
                "The online search failed; no information was fabricated."));
    }

    private List<MedicalResult> searchMedlinePlus(String query, boolean spanish) throws Exception {
        if (spanish) {
            return searchSpanishMedlinePlus(query);
        }
        String url = "https://wsearch.nlm.nih.gov/ws/query?db=healthTopics&term="
                + encode(query) + "&retmax=10";
        Document document = fetchXml(url);
        NodeList documents = document.getElementsByTagName("document");
        List<MedicalResult> results = new ArrayList<>();
        for (int i = 0; i < documents.getLength(); i++) {
            Element entry = (Element) documents.item(i);
            String title = cleanSummary(childContent(entry, "title"));
            String summary = cleanSummary(childContent(entry, "FullSummary"));
            if (summary.isBlank()) {
                summary = cleanSummary(childContent(entry, "snippet"));
            }
            String articleUrl = entry.getAttribute("url");
            if (!title.isBlank() && articleUrl.startsWith("https://medlineplus.gov/")) {
                results.add(new MedicalResult(title, cleanSummary(summary), articleUrl));
            }
        }
        return results;
    }

    private List<MedicalResult> searchSpanishMedlinePlus(String query) throws Exception {
        String url = "https://vsearch.nlm.nih.gov/vivisimo/cgi-bin/query-meta"
                + "?v%3Aproject=medlineplus-spanish"
                + "&v%3Asources=medlineplus-spanish-bundle"
                + "&query=" + encode(query);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(18))
                .header("User-Agent", "MedEstudio/1.0 (educational desktop application)")
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("La búsqueda en español respondió con HTTP "
                    + response.statusCode() + ".");
        }

        List<MedicalResult> results = new ArrayList<>();
        new ParserDelegator().parse(new StringReader(response.body()), new HTMLEditorKit.ParserCallback() {
            private String href;
            private final StringBuilder anchorText = new StringBuilder();

            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.A) {
                    Object classValue = attributes.getAttribute(HTML.Attribute.CLASS);
                    boolean resultTitle = classValue != null
                            && List.of(classValue.toString().split("\\s+")).contains("title");
                    Object value = attributes.getAttribute(HTML.Attribute.HREF);
                    href = resultTitle && value != null ? value.toString() : null;
                    anchorText.setLength(0);
                }
            }

            @Override
            public void handleText(char[] data, int position) {
                if (href != null) {
                    anchorText.append(data);
                }
            }

            @Override
            public void handleEndTag(HTML.Tag tag, int position) {
                if (tag != HTML.Tag.A || href == null) {
                    return;
                }
                String articleUrl = extractSpanishArticleUrl(href);
                String title = anchorText.toString().trim();
                if (!articleUrl.isBlank() && !title.isBlank()
                        && results.stream().noneMatch(result -> result.url().equals(articleUrl))) {
                    results.add(new MedicalResult(title, "", articleUrl));
                }
                href = null;
                anchorText.setLength(0);
            }
        }, true);
        return results.size() > 10 ? new ArrayList<>(results.subList(0, 10)) : results;
    }

    private static String extractSpanishArticleUrl(String href) {
        String candidate = href;
        Matcher redirectUrl = Pattern.compile("[?&]url=([^&]+)", Pattern.CASE_INSENSITIVE).matcher(href);
        if (redirectUrl.find()) {
            candidate = URLDecoder.decode(redirectUrl.group(1), StandardCharsets.UTF_8);
        } else if (candidate.startsWith("/")) {
            candidate = "https://medlineplus.gov" + candidate;
        }

        try {
            URI uri = URI.create(candidate);
            String path = uri.getPath();
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && "medlineplus.gov".equalsIgnoreCase(uri.getHost())
                    && path != null
                    && path.matches("/spanish/[^?#]+\\.html")) {
                return uri.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed or non-MedlinePlus search links.
        }
        return "";
    }

    private List<CommonsImage> searchCommonsImages(String query) throws Exception {
        String url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search"
                + "&gsrsearch=" + encode(query) + "&gsrnamespace=6&gsrlimit=8"
                + "&prop=imageinfo&iiprop=url&iiurlwidth=700&format=xml";
        Document document = fetchXml(url);
        NodeList pages = document.getElementsByTagName("page");
        List<CommonsImage> images = new ArrayList<>();
        for (int i = 0; i < pages.getLength(); i++) {
            Element page = (Element) pages.item(i);
            NodeList imageInfoNodes = page.getElementsByTagName("ii");
            if (imageInfoNodes.getLength() == 0) {
                continue;
            }
            Element imageInfo = (Element) imageInfoNodes.item(0);
            String thumbnailUrl = imageInfo.getAttribute("thumburl");
            String sourceUrl = imageInfo.getAttribute("descriptionurl");
            if (!thumbnailUrl.isBlank() && sourceUrl.startsWith("https://commons.wikimedia.org/")) {
                try {
                    BufferedImage image = downloadImage(thumbnailUrl);
                    images.add(new CommonsImage(page.getAttribute("title").replaceFirst("^File:", ""),
                            sourceUrl, fitImage(image, 320, 240)));
                    if (images.size() == 3) {
                        break;
                    }
                } catch (Exception ignored) {
                    // Skip a broken Commons thumbnail while keeping other search images available.
                }
            }
        }
        return images;
    }

    private static Document fetchXml(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(18))
                .header("User-Agent", "MedEstudio/1.0 (educational desktop application)")
                .header("Accept", "application/xml,text/xml")
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("El servicio respondió con HTTP " + response.statusCode() + ".");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(response.body()));
    }

    private static BufferedImage downloadImage(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(18))
                .header("User-Agent", "MedEstudio/1.0 (educational desktop application)")
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("No se pudo descargar la imagen (HTTP " + response.statusCode() + ").");
        }
        BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(response.body()));
        if (image == null) {
            throw new IllegalStateException("El archivo recibido no es una imagen compatible.");
        }
        return image;
    }

    private static BufferedImage fitImage(BufferedImage original, int maxWidth, int maxHeight) {
        double scale = Math.min((double) maxWidth / original.getWidth(),
                (double) maxHeight / original.getHeight());
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        Image scaled = original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(scaled, 0, 0, null);
        graphics.dispose();
        return result;
    }

    private void setSearching(boolean searching) {
        searchButton.setEnabled(!searching);
        searchField.setEnabled(!searching);
        languageSelector.setEnabled(!searching);
        for (JButton button : quickSearchButtons) {
            button.setEnabled(!searching);
        }
    }

    private void addHeading(String text) {
        JLabel heading = new JLabel(escapeHtml(text));
        heading.setFont(new Font("SansSerif", Font.BOLD, 17));
        heading.setForeground(NAVY);
        heading.setAlignmentX(0.0f);
        heading.setBorder(BorderFactory.createEmptyBorder(2, 0, 7, 0));
        resultsPanel.add(heading);
    }

    private void addParagraph(String text) {
        JTextArea paragraph = new JTextArea(text);
        paragraph.setFont(BODY_FONT);
        paragraph.setForeground(TEXT);
        paragraph.setLineWrap(true);
        paragraph.setWrapStyleWord(true);
        paragraph.setEditable(false);
        paragraph.setFocusable(false);
        paragraph.setOpaque(false);
        paragraph.setAlignmentX(0.0f);
        paragraph.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        paragraph.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        resultsPanel.add(paragraph);
    }

    private void addLinkButton(String label, String url) {
        JButton link = new JButton(label);
        link.setFont(new Font("SansSerif", Font.PLAIN, 13));
        link.setForeground(TEAL);
        link.setHorizontalAlignment(SwingConstants.LEFT);
        link.setAlignmentX(0.0f);
        link.setBorderPainted(false);
        link.setContentAreaFilled(false);
        link.setFocusPainted(false);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addActionListener(event -> openUrl(url));
        resultsPanel.add(link);
    }

    private void addSeparator() {
        JPanel line = new JPanel();
        line.setBackground(new Color(230, 237, 239));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setPreferredSize(new Dimension(100, 1));
        line.setAlignmentX(0.0f);
        resultsPanel.add(Box.createVerticalStrut(8));
        resultsPanel.add(line);
        resultsPanel.add(Box.createVerticalStrut(8));
    }

    private static String childContent(Element parent, String name) {
        NodeList contents = parent.getElementsByTagName("content");
        for (int i = 0; i < contents.getLength(); i++) {
            Element content = (Element) contents.item(i);
            if (name.equals(content.getAttribute("name"))) {
                return content.getTextContent();
            }
        }
        return "";
    }

    private static String cleanSummary(String html) {
        return html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</?(p|h[1-6]|ul|ol|table|tr)\\b[^>]*>", "\n")
                .replaceAll("(?i)<li\\b[^>]*>", "\n• ")
                .replaceAll("(?i)</li\\s*>", "\n")
                .replaceAll("(?i)</t[dh]\\s*>", " | ")
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .replaceAll("(?m)^[ \\t]+", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void changeLanguage() {
        updateLanguage();
        if (lastQuery.isBlank()) {
            showWelcome();
        } else {
            search(lastQuery);
        }
    }

    private void updateLanguage() {
        boolean spanish = isSpanish();
        frame.setTitle(spanish ? "MedEstudio | Asistente de aprendizaje" : "MedEstudio | Study assistant");
        tabs.setTitleAt(0, tr("Buscar", "Search"));
        tabs.setTitleAt(1, tr("Chat con IA", "AI chat"));
        subtitleLabel.setText(tr("Un punto de partida confiable para estudiar medicina",
                "A reliable starting point for studying medicine"));
        searchButton.setText(tr("Buscar", "Search"));
        searchField.setToolTipText(tr("Ejemplo: diabetes, anemia, corazón, asma...",
                "Example: diabetes, anemia, heart, asthma..."));
        imageTitleLabel.setText(tr("Apoyo visual", "Visual aid"));
        imageSourceButton.setText(tr("Ver imagen y licencia", "View image and license"));
        imageLabel.setText(tr("Las imágenes del tema aparecerán aquí", "Topic images will appear here"));
        chatIntroLabel.setText("<html><b>" + tr("Asistente de estudio con " + chatAssistantName(),
                "Study assistant with " + chatAssistantName()) + "</b><br>"
                + (aiProvider.equals("ollama")
                ? tr("Responde en tu computadora; para fuentes actuales usa la pestaña Buscar.",
                        "Runs on your computer; use the Search tab for current sources.")
                : tr("Puede consultar la web y conservar el contexto del chat.",
                        "Can search the web and keep chat context.")) + "</html>");
        configureKeyButton.setText(tr("Configurar IA", "Configure AI"));
        newChatButton.setText(tr("Nuevo chat", "New chat"));
        sendChatButton.setText(tr("Enviar", "Send"));
        chatInput.setToolTipText(tr("Escribe tu pregunta y pulsa Enviar", "Type your question and press Send"));
        chatPrivacyLabel.setText(aiProvider.equals("ollama")
                ? tr("No incluyas datos privados de pacientes. Con Ollama, las preguntas se procesan localmente en esta computadora.",
                        "Do not include private patient information. With Ollama, questions are processed locally on this computer.")
                : tr("No incluyas datos privados de pacientes. Las preguntas se envían a Google; pueden aplicarse límites o cargos según tu cuenta.",
                        "Do not include private patient information. Questions are sent to Google; account quotas or charges may apply."));
        if (chatHistory.isEmpty()) {
            showChatWelcome();
        }

        String[] spanishTopics = {"Diabetes", "Anemia", "Asma", "Hipertensión", "Migraña", "Neumonía",
                "Artritis", "Gastritis", "Depresión", "Infección urinaria", "COVID-19", "Infarto"};
        String[] englishTopics = {"Diabetes", "Anemia", "Asthma", "High blood pressure", "Migraine", "Pneumonia",
                "Arthritis", "Gastritis", "Depression", "Urinary infection", "COVID-19", "Heart attack"};
        for (int i = 0; i < quickSearchButtons.length; i++) {
            quickSearchButtons[i].setText(spanish ? spanishTopics[i] : englishTopics[i]);
        }
        if (spanish) {
            imageLabel.setText("Las imágenes del tema aparecerán aquí");
        } else {
            imageLabel.setText("Topic images will appear here");
        }
    }

    private String quickTopic(int index) {
        String[][] topics = {
                {"Diabetes", "Diabetes"},
                {"Anemia", "Anemia"},
                {"Asma", "Asthma"},
                {"Hipertensión", "High blood pressure"},
                {"Migraña", "Migraine"},
                {"Neumonía", "Pneumonia"},
                {"Artritis", "Arthritis"},
                {"Gastritis", "Gastritis"},
                {"Depresión", "Depression"},
                {"Infección urinaria", "Urinary infection"},
                {"COVID-19", "COVID-19"},
                {"Infarto", "Heart attack"}
        };
        return topics[index][isSpanish() ? 0 : 1];
    }

    private void showImage(int index) {
        if (currentImages.isEmpty()) {
            return;
        }
        currentImageIndex = Math.floorMod(index, currentImages.size());
        CommonsImage image = currentImages.get(currentImageIndex);
        imageLabel.setText("");
        imageLabel.setIcon(new ImageIcon(image.image()));
        imageCaption.setText("<html>" + tr("Imagen ", "Image ")
                + (currentImageIndex + 1) + "/" + currentImages.size() + ": "
                + escapeHtml(image.title()) + "<br>"
                + tr("Fuente: Wikimedia Commons", "Source: Wikimedia Commons") + "</html>");
        imageSourceButton.setEnabled(true);
        previousImageButton.setEnabled(currentImages.size() > 1);
        nextImageButton.setEnabled(currentImages.size() > 1);
    }

    private boolean isSpanish() {
        return languageSelector.getSelectedIndex() == 0;
    }

    private String tr(String spanish, String english) {
        return isSpanish() ? spanish : english;
    }

    private static String spanishArticleUrl(String url) {
        if (url.startsWith("https://medlineplus.gov/spanish/")) {
            return url;
        }
        return url.replaceFirst("^https://medlineplus\\.gov/", "https://medlineplus.gov/spanish/");
    }

    private static String medlinePlusSearchUrl(String query, boolean spanish) {
        return "https://vsearch.nlm.nih.gov/vivisimo/cgi-bin/query-meta"
                + "?v%3Aproject=" + (spanish ? "medlineplus-spanish" : "medlineplus")
                + "&v%3Asources=" + (spanish ? "medlineplus-spanish-bundle" : "medlineplus-bundle")
                + "&query=" + encode(query);
    }

    private SpanishArticle fetchSpanishArticle(MedicalResult result) throws Exception {
        String url = spanishArticleUrl(result.url());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(18))
                .header("User-Agent", "MedEstudio/1.0 (educational desktop application)")
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("La página en español respondió con HTTP "
                    + response.statusCode() + ".");
        }
        SpanishArticle article = extractSpanishArticle(response.body(), result.title());
        if (article.summary().isBlank()) {
            throw new IllegalStateException("La página no contenía el resumen en español.");
        }
        return article;
    }

    private static SpanishArticle extractSpanishArticle(String html, String fallbackTitle) throws Exception {
        StringBuilder summary = new StringBuilder();
        String[] title = {fallbackTitle};
        boolean[] inSummary = {false};
        int[] divDepth = {0};
        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.DIV && !inSummary[0]
                        && "topic-summary".equals(String.valueOf(attributes.getAttribute(HTML.Attribute.ID)))) {
                    inSummary[0] = true;
                    divDepth[0] = 1;
                    return;
                }
                if (!inSummary[0]) {
                    return;
                }
                if (tag == HTML.Tag.DIV) {
                    divDepth[0]++;
                } else if (tag == HTML.Tag.H3) {
                    summary.append("\n\n");
                } else if (tag == HTML.Tag.P) {
                    summary.append('\n');
                } else if (tag == HTML.Tag.LI) {
                    summary.append("\n• ");
                }
            }

            @Override
            public void handleEndTag(HTML.Tag tag, int position) {
                if (!inSummary[0]) {
                    return;
                }
                if (tag == HTML.Tag.DIV) {
                    divDepth[0]--;
                    if (divDepth[0] == 0) {
                        inSummary[0] = false;
                    }
                } else if (tag == HTML.Tag.H3 || tag == HTML.Tag.P || tag == HTML.Tag.LI) {
                    summary.append('\n');
                }
            }

            @Override
            public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (!inSummary[0] && tag == HTML.Tag.META
                        && "DC.Title".equalsIgnoreCase(String.valueOf(attributes.getAttribute(HTML.Attribute.NAME)))) {
                    Object articleTitle = attributes.getAttribute(HTML.Attribute.CONTENT);
                    if (articleTitle != null && !articleTitle.toString().isBlank()) {
                        title[0] = articleTitle.toString();
                    }
                } else if (inSummary[0] && tag == HTML.Tag.BR) {
                    summary.append('\n');
                }
            }

            @Override
            public void handleText(char[] data, int position) {
                if (inSummary[0]) {
                    summary.append(data);
                }
            }
        };
        new ParserDelegator().parse(new StringReader(html), callback, true);
        String cleanText = summary.toString()
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return new SpanishArticle(title[0], cleanText);
    }

    private static String limitText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        int end = text.lastIndexOf(' ', maxLength);
        if (end < maxLength / 2) {
            end = maxLength;
        }
        return text.substring(0, end).trim() + "\n\n...";
    }

    private static String translate(String text) throws Exception {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder translated = new StringBuilder();
        int offset = 0;
        while (offset < text.length()) {
            int end = Math.min(offset + 450, text.length());
            if (end < text.length()) {
                int boundary = text.lastIndexOf(' ', end);
                if (boundary > offset + 150) {
                    end = boundary;
                }
            }
            String chunk = text.substring(offset, end).trim();
            if (!chunk.isEmpty()) {
                String url = "https://api.mymemory.translated.net/get?q=" + encode(chunk)
                        + "&langpair=en%7Ces";
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(18))
                        .header("User-Agent", "MedEstudio/1.0 (educational desktop application)")
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("El servicio de traducción respondió con HTTP "
                            + response.statusCode() + ".");
                }
                if (!response.body().contains("\"responseStatus\":200")) {
                    throw new IllegalStateException("El servicio de traducción no pudo traducir el resumen.");
                }
                Matcher matcher = Pattern.compile("\"translatedText\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                        .matcher(response.body());
                if (!matcher.find()) {
                    throw new IllegalStateException("El servicio de traducción no devolvió un texto válido.");
                }
                if (!translated.isEmpty()) {
                    translated.append(' ');
                }
                translated.append(decodeJsonString(matcher.group(1)));
            }
            offset = end;
        }
        return translated.toString().trim();
    }

    private static String decodeJsonString(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                result.append(current);
                continue;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (i + 4 >= value.length()) {
                        throw new IllegalArgumentException("Respuesta JSON de traducción incompleta.");
                    }
                    result.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> result.append(escaped);
            }
        }
        return result.toString().replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String extractGeminiText(String responseBody) {
        Matcher outputMatcher = Pattern.compile("\"type\"\\s*:\\s*\"model_output\"").matcher(responseBody);
        if (!outputMatcher.find()) {
            return "";
        }
        Matcher matcher = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .matcher(responseBody.substring(outputMatcher.start()));
        StringBuilder answer = new StringBuilder();
        while (matcher.find()) {
            if (!answer.isEmpty()) {
                answer.append('\n');
            }
            answer.append(decodeJsonString(matcher.group(1)));
        }
        return answer.toString().trim();
    }

    private static String extractGeminiCitations(String responseBody) {
        Matcher objects = Pattern.compile("\\{[^{}]*\"type\"\\s*:\\s*\"url_citation\"[^{}]*}")
                .matcher(responseBody);
        List<String> citations = new ArrayList<>();
        while (objects.find()) {
            String citation = objects.group();
            String url = jsonField(citation, "url");
            String title = jsonField(citation, "title");
            if ((url.startsWith("https://") || url.startsWith("http://")) && !citations.contains(url)) {
                citations.add((title.isBlank() ? url : title) + " — " + url);
            }
        }
        return String.join("\n", citations);
    }

    private static String jsonField(String json, String fieldName) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(fieldName)
                        + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .matcher(json);
        return matcher.find() ? decodeJsonString(matcher.group(1)) : "";
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 177) + "..." : message;
    }

    private static void openUrl(String value) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new UnsupportedOperationException("Este sistema no permite abrir enlaces desde la aplicación.");
            }
            Desktop.getDesktop().browse(URI.create(value));
        } catch (Exception exception) {
            javax.swing.JOptionPane.showMessageDialog(null,
                    "No se pudo abrir el enlace: " + safeMessage(exception),
                    "No se pudo abrir el enlace", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private record MedicalResult(String title, String summary, String url) {
    }

    private record ChatMessage(String role, String text) {
    }

    private record SpanishArticle(String title, String summary) {
    }

    private record CommonsImage(String title, String sourceUrl, BufferedImage image) {
    }

    private record SearchBundle(List<MedicalResult> results, List<CommonsImage> images,
                                String imageError, boolean translationFailed) {
    }
}
