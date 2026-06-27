package TF_ESII.TF.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;

@Configurable
public class AgentConfig {
    
    @Bean
    public ChatClient chatclient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystemMessage("""
                    Você é um assistente inteligente e prestativo.

                    Você tem acesso a ferramentas que pode usar para buscar informações.
                    SEMPRE verifique a base de conhecimento antes de responder perguntas
                    sobre o sistema ou seus dados.

                    Processo que você DEVE seguir:
                    1. Analise a pergunta do usuário.
                    2. Se precisar de informações externas, use uma ferramenta.
                    3. Use os resultados da ferramenta para formular a resposta.
                    4. Responda de forma clara, objetiva e em português.

                    Quando não souber algo, diga claramente ao invés de inventar.
                    """)
                    .build();
    }
}
