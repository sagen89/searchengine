package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.LemmaEntity;


import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class LemmaFinder {
    private final LuceneMorphology luceneMorphology;
    private static final String WORD_TYPE_REGEX = "\\W\\w&&[^А-я\\s]";
    private static final String[] PARTICLES_Names = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    @Autowired
    public LemmaFinder() throws IOException{
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    protected HashMap<String, Integer> collectLemmas(String text) {
        List<String> words = splittingTextByWords(text);
        HashMap<String, Integer> mapOfLemmasAndFrequencies = new HashMap<>(words.size());

        for (String word : words) {
            List<String> normalForms = getNormalForms(word);
            if (normalForms.size() == 0) {
                continue;
            }
            String normalWord = normalForms.get(0);
            mapOfLemmasAndFrequencies.put(normalWord,
                    mapOfLemmasAndFrequencies.getOrDefault(normalWord, 0) + 1);
        }

        return mapOfLemmasAndFrequencies;
    }

    protected List<LemmaEntity> collectLemmasEntity(String text) {
        List<String> words = splittingTextByWords(text);

        List<LemmaEntity> lemmaEntities = new ArrayList<>(words.size());

        for (String word : words) {
            List<String> normalForms = getNormalForms(word);
            if (normalForms.size() > 0) {
                LemmaEntity lemmaEntity = new LemmaEntity();
                lemmaEntity.setLemma(normalForms.get(0));
                lemmaEntity.setWord(word);
                lemmaEntities.add(lemmaEntity);
            }
        }

        return lemmaEntities;
    }



    protected List<String> getUniqueWords(String text) {
        List<String> words = splittingTextByWords(text.toLowerCase());
        return words.stream().filter(word ->{
            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            return !word.isEmpty() && isCorrectWordForm(word)
                    && !anyWordBaseBelongToParticle(wordBaseForms);
        }).distinct().collect(Collectors.toList());
    }

    protected List<String> getNormalForms(String word) {
        if (word.isBlank()) {
            return Collections.emptyList();
        }

        String lowerCaseWord = word.toLowerCase();

        List<String> wordBaseForms = luceneMorphology.
                getMorphInfo(lowerCaseWord);
        if (anyWordBaseBelongToParticle(wordBaseForms)) {
            return Collections.emptyList();
        }

        List<String> normalForms = luceneMorphology.
                getNormalForms(lowerCaseWord);
        if (normalForms.isEmpty()) {
            return Collections.emptyList();
        }

        return normalForms;
    }

    protected String htmlCodeToTextWhitRussianWords(Document htmlCode) {
        return htmlCode.text().replaceAll("[^А-яЁё]+", " ");
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : PARTICLES_Names) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private List<String> splittingTextByWords(String text) {
        return Arrays.stream(text.trim().split("\\s+")).filter(word ->
                    word.length() > 2).toList();
    }

    private boolean isCorrectWordForm(String word) {
        List<String> wordInfo = luceneMorphology.getMorphInfo(word);
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(WORD_TYPE_REGEX)) {
                return false;
            }
        }
        return true;
    }
}

