import configuration.WindowConfiguration;
import configuration.operations.ConfigurationOperation;
import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.WordNetDatabase;
import relatedness.SynsetRelatedness;
import utils.POSUtils;
import utils.SynsetUtils;
import utils.WordUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Butnaru Andrei-Madalin.
 */
public class ShotgunWSDLocal {
    private int[] windowWordsSynsetStart;
    private int[] windowWordsSynsetLength;
    private double[][] synsetPairScores;
    protected int[] synset2WordIndex;
    private int[] realWindowIndex;
    protected Synset[] windowWordsSynsets;

    private LinkedList<WindowConfiguration> windowSolutions;

    private String[] windowWords;
    private String[] windowWordsPOS;
    private int numberConfigs, offset;

    private SynsetRelatedness synsetRelatedness;

    /**
     * @param offset         Global index of the first word in the context window
     * @param windowWords    An array of windows that we want to disambiguate
     * @param windowWordsPOS An array of POS Tags for each word in the windowWords param
     * @param numberConfigs  Number of sense configurations considered for the voting scheme
     */
    public ShotgunWSDLocal(int offset, String[] windowWords, String[] windowWordsPOS, int[] realWindowIndex, int numberConfigs, SynsetRelatedness synsetRelatedness) {
        this.offset = offset;
        this.windowWords = windowWords;
        this.windowWordsPOS = windowWordsPOS;
        this.numberConfigs = numberConfigs;
        this.realWindowIndex = realWindowIndex;
        this.synsetRelatedness = synsetRelatedness;
        
        //System.out.println("In ShotgunWSDLocal: " + "offset: " + offset + " word: " + windowWords[0]);

        windowWordsSynsetStart = new int[windowWords.length];
        windowWordsSynsetLength = new int[windowWords.length];
        windowSolutions = new LinkedList<>();
    }

    /**
     * Run the WSD Algorithm for the local window
     *
     * @param wnDatabase WordNet Database
     */
    public void run(WordNetDatabase wnDatabase) {
        buildWindowSynsetsArray(wnDatabase);
        buildSynsetMapping();
        Object[] synsetRepresentations = synsetRelatedness.computeSynsetRepresentations(windowWordsSynsets, windowWords, synset2WordIndex);
        computeWordPairSynsetRelatedness(synsetRepresentations);

        generateSynsetCombinations();
    }

    /**
     * For each pair of synsets, compute the similarity between them.
     */
    public void computeWordPairSynsetRelatedness(Object[] synsetRepresentations) {
        double sim;

        synsetPairScores = new double[windowWordsSynsets.length][windowWordsSynsets.length];
        for (int j = 0; j < windowWordsSynsets.length; j++) {
            for (int k = j; k < windowWordsSynsets.length; k++) {
                sim = synsetRelatedness.computeSimilarity(synsetRepresentations, windowWords, synset2WordIndex, k, j);
                synsetPairScores[j][k] = sim;
                synsetPairScores[k][j] = sim;
            }
        }
    }

    /**
     * Build an array that stores, for each synset the word from the context window that represents.
     */
    protected void buildSynsetMapping() {
        int lastIdx;
        synset2WordIndex = new int[windowWordsSynsets.length];
        for (int j = 0; j < windowWordsSynsetStart.length; j++) {
            lastIdx = j + 1 == windowWordsSynsetStart.length ? windowWordsSynsets.length : windowWordsSynsetStart[j + 1];
            for (int k = windowWordsSynsetStart[j]; k < lastIdx; k++) {
                synset2WordIndex[k] = j;
            }
        }

    }

    /**
     * This method build and store an array that contains al synsets for all the words in the context window.
     * This is usefully to keep a simple representation of a synset, and to access it fast, without the need to look in WordNet.
     *
     * @param wnDatabase WordNet Database
     */
    protected void buildWindowSynsetsArray(WordNetDatabase wnDatabase) {
        ArrayList<Synset> windowSynsets = new ArrayList<>();

        Synset[] tmpSynsets;
        int synsetStartIndex = 0, synsetLength;

        // For each word in the context window.
        for (int j = 0; j < windowWords.length; j++) {
            // Extract the words synsets
            tmpSynsets = WordUtils.extractSynsets(wnDatabase, windowWords[j], POSUtils.asSynsetType(windowWordsPOS[j]));
            //System.out.println("In local: " + windowWords[j] + " " + Arrays.asList(tmpSynsets) );
            // Insert there synsets to an array
            synsetLength = tmpSynsets.length;
            if (tmpSynsets.length == 0) {
                synsetLength += 1;
                windowSynsets.add(null);
            } else {
                windowSynsets.addAll(Arrays.asList(tmpSynsets));
            }

            // Set the start index and the number of synsets
            windowWordsSynsetStart[j] = synsetStartIndex;
            windowWordsSynsetLength[j] = synsetLength;
            
            // Increment the index position, for the new word
            synsetStartIndex += synsetLength;
        }

        windowWordsSynsets = new Synset[windowSynsets.size()];
        windowWordsSynsets = windowSynsets.toArray(windowWordsSynsets);
        //System.out.println("In local: " + " " + Arrays.asList(windowWordsSynsets));
    
    }
    private void generateSynsetCombinations() {
        int wordIndex = 0;

        generateSynsetCombinations(wordIndex, new int[windowWords.length]);

        if(windowSolutions.size() == 0){
            windowSolutions = null;
        } else {
            for(WindowConfiguration windowConfiguration: windowSolutions)
                {
            		windowConfiguration.setGlobalIDSCustom(offset, synset2WordIndex, windowWordsSynsetStart, realWindowIndex);
            		//System.out.println(windowWords[wordIndex] + " " + Arrays.asList(windowConfiguration));
                }
        }
    }

    // TODO write docs
    private void generateSynsetCombinations(int wordIndex, int[] synsets){
        double score;
        int size;
        Synset[] configurationSynsets;
        
        for (int i = windowWordsSynsetStart[wordIndex]; i < windowWordsSynsetStart[wordIndex] + windowWordsSynsetLength[wordIndex]; i++) {
            synsets[wordIndex] = i;

            if(wordIndex < windowWords.length - 1) {
                generateSynsetCombinations(wordIndex + 1, synsets);
            } else {
            	
            		
                score = SynsetUtils.computeConfigurationScore(synsets, synsetPairScores);
                configurationSynsets = SynsetUtils.getSynsets(synsets, windowWordsSynsets);

                size = windowSolutions.size();
                if(size >= this.numberConfigs) {
                    if(score >= windowSolutions.get(windowSolutions.size() - 1).getScore()){
                        windowSolutions.addLast(new WindowConfiguration(synsets.clone(), windowWords, windowWordsPOS, configurationSynsets, score));
                        windowSolutions.sort(WindowConfiguration.windowConfigurationComparator);
                        windowSolutions.pollFirst();
                    }
                } else {
                    windowSolutions.push(new WindowConfiguration(synsets.clone(), windowWords, windowWordsPOS, configurationSynsets, score));

                    if(size == this.numberConfigs - 1) {
                        windowSolutions.sort(WindowConfiguration.windowConfigurationComparator);
                    }
                }
            }
        }
    }

    public List<WindowConfiguration> getWindowSolutions(){
        return windowSolutions;
    }
}
