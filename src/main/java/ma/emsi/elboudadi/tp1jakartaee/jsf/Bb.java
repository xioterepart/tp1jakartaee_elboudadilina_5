package ma.emsi.elboudadi.tp1jakartaee.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import ma.emsi.elboudadi.tp1jakartaee.llm.JsonUtilPourGemini;
import ma.emsi.elboudadi.tp1jakartaee.llm.LlmInteraction;
import ma.emsi.elboudadi.tp1jakartaee.llm.RequeteException;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Portée view pour conserver l'état de la conversation qui dure pendant plusieurs requêtes HTTP.
 * La portée view nécessite l'implémentation de Serializable (le backing bean peut être mis en mémoire secondaire).
 */
@Named
@ViewScoped
public class Bb implements Serializable {

    /**
     * Rôle "système" que l'on attribuera plus tard à un LLM.
     * Valeur par défaut que l'utilisateur peut modifier.
     * Possible d'écrire un nouveau rôle dans la liste déroulante.
     */
    private String roleSysteme;

    /**
     * Quand le rôle est choisi par l'utilisateur dans la liste déroulante,
     * il n'est plus possible de le modifier (voir code de la page JSF), sauf si on veut un nouveau chat.
     */
    private boolean roleSystemeChangeable = true;

    /**
     * Liste de tous les rôles de l'API prédéfinis.
     */
    private List<SelectItem> listeRolesSysteme;

    /**
     * Dernière question posée par l'utilisateur.
     */
    private String question;
    /**
     * Dernière réponse de l'API OpenAI.
     */
    private String reponse;
    /**
     * La conversation depuis le début.
     */
    private StringBuilder conversation = new StringBuilder();

    private String texteRequeteJson;
    private String texteReponseJson;

    @Inject
    private JsonUtilPourGemini jsonUtil;



    private boolean debug = false;

    public boolean isDebug() {

        return debug;
    }
    public void toggleDebug() {



        this.setDebug(!isDebug());
    }
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Contexte JSF. Utilisé pour qu'un message d'erreur s'affiche dans le formulaire.
     */
    @Inject
    private FacesContext facesContext;

    /**
     * Obligatoire pour un bean CDI (classe gérée par CDI), s'il y a un autre constructeur.
     */
    public Bb() {
    }

    public String getRoleSysteme() {
        return roleSysteme;
    }

    public void setRoleSysteme(String roleSysteme) {
        this.roleSysteme = roleSysteme;
    }

    public boolean isRoleSystemeChangeable() {
        return roleSystemeChangeable;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return reponse;
    }

    /**
     * setter indispensable pour le textarea.
     *
     * @param reponse la réponse à la question.
     */
    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public String getConversation() {
        return conversation.toString();
    }

    public void setConversation(String conversation) {
        this.conversation = new StringBuilder(conversation);
    }

    /*
     * Envoie la question au serveur.
     * En attendant de l'envoyer à un LLM, le serveur fait un traitement quelconque, juste pour tester :
     * Le traitement consiste à copier la question en minuscules et à l'entourer avec "||". Le rôle système
     * est ajouté au début de la première réponse.
     *
     * @return null pour rester sur la même page.
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Texte question vide", "Il manque le texte de la question");
            facesContext.addMessage(null, message);
            return null;
        }

        jsonUtil.setSystemRole(roleSysteme);

        try {
            LlmInteraction interaction = jsonUtil.envoyerRequete(question);
            this.reponse = interaction.reponseExtraite();
            this.texteRequeteJson = interaction.questionJson();
            this.texteReponseJson = interaction.reponseJson();
        } catch (RequeteException e) {
            FacesMessage message =
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Problème de connexion avec l'API du LLM",
                            "Problème de connexion avec l'API du LLM" + e.getMessage());
            facesContext.addMessage(null, message);
        }

        // Mise à jour de la conversation
        afficherConversation();

        // Une fois qu’on a envoyé la question, on bloque le rôle système si c’est le premier message
        if (this.conversation.toString().split("== User:").length <= 2) { // Correction de la condition
            this.roleSystemeChangeable = false;
        }

        return null; // reste sur la même page
    }

    /**
     * Pour un nouveau chat.
     * Termine la portée view en retournant "index" (la page index.xhtml sera affichée après le traitement
     * effectué pour construire la réponse) et pas null. null aurait indiqué de rester dans la même page (index.xhtml)
     * sans changer de vue.
     * Le fait de changer de vue va faire supprimer l'instance en cours du backing bean par CDI et donc on reprend
     * tout comme au début puisqu'une nouvelle instance du backing va être utilisée par la page index.xhtml.
     * @return "index"
     */
    public String nouveauChat() {
        return "index";
    }

    /**
     * Pour afficher la conversation dans le textArea de la page JSF.
     */
    private void afficherConversation() {
        this.conversation.append("== User:\n").append(question).append("\n== Serveur:\n").append(reponse).append("\n");
    }

    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            // Génère les rôles de l'API prédéfinis
            this.listeRolesSysteme = new ArrayList<>();
            // Vous pouvez évidemment écrire ces rôles dans la langue que vous voulez.
            String role = """
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it.
                    """;
            // 1er argument : la valeur du rôle, 2ème argument : le libellé du rôle
            this.listeRolesSysteme.add(new SelectItem(role, "Assistant"));

            role = """
                    You are an interpreter. You translate from English to French and from French to English.
                    If the user type a French text, you translate it into English.
                    If the user type an English text, you translate it into French.
                    If the text contains only one to three words, give some examples of usage of these words in English.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Traducteur Anglais-Français"));

            role = """
                    Your are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit in the country or the town
                    are you tell them the average price of a meal.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Guide touristique"));

            role = """
                    Spécialisé dans les recherches locales : restaurants, cafés, hôtels, lieux touristiques, etc.
                    🪄 Exemples :

                    •“Trouve-moi un restaurant italien à Casablanca.”
                    •“Y a-t-il un hôtel proche des cascades d’Akchour ?”""";
            this.listeRolesSysteme.add(new SelectItem(role, "Recherche locale"));

            // --- NOUVEAUX RÔLES ---

            role = """
                    Vous êtes un développeur logiciel professionnel, spécialisé en Java et Jakarta EE.
                    Si l'utilisateur demande du code, fournissez un extrait de code complet, fonctionnel et bien commenté.
                    Si l'utilisateur demande une explication d'un concept, fournissez une réponse claire, concise et techniquement précise avec des exemples pertinents.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Développeur Java/Jakarta EE"));

            role = """
                    Vous êtes un conseiller financier. Vous fournissez des conseils généraux sur les finances personnelles,
                    l'investissement et la budgétisation. Vous devez toujours commencer votre réponse en indiquant :
                    "Ceci n'est pas un conseil financier personnalisé. Consultez un professionnel certifié."
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Conseiller Financier"));

            role = """
                    Vous êtes un écrivain créatif et un conteur. Lorsque l'utilisateur fournit un sujet ou un thème,
                    vous devez écrire une courte histoire ou un poème engageant qui intègre son entrée.
                    Utilisez toujours un langage évocateur et concentrez-vous sur les éléments narratifs.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Écrivain Créatif"));

        }

        return this.listeRolesSysteme;
    }

    public String getTexteRequeteJson() {
        return texteRequeteJson;
    }

    public String getTexteReponseJson() {
        return texteReponseJson;
    }

}