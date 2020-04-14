package termSelector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File; 
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Arrays;

//import it.unimi.dsi.fastutil.Arrays;

import java.util.Set;
import java.util.TreeMap;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.rules.RuleMatch;

import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.WordNetDatabase;

public class TermSelectionAlgo {
	private Map<String, double[]> word_vec_yelp = new HashMap<String, double[]>();
	private Map<String, double[]> word_vec_google = new HashMap<String, double[]>();
	private Map<String, String> allTerms = new HashMap<String, String>();
	private Map<String,double[]> word_term_score = new HashMap<String, double[]>();
	private Map<Double, String> term_scores_test = new TreeMap<Double, String>(new DescOrder());
	private Map<String,String> aspect_mentions = new HashMap<String, String>();
	private Map<String,Integer> sentiment_mentions = new HashMap<String, Integer>();

	private Set<double[]> mention_classes_vec = new HashSet<double[]>();
	private List<Double> term_scores= new ArrayList<Double>();
	private Map<String, String> acceptedTerms = new HashMap<String, String>();
	
	private List<String> mention_words;
	
	private double max_score_noun;
	private double max_score_adj;
	private double max_score_verb;
	
	public TermSelectionAlgo(String filelocation_google, String filelocation_yelp, String filelocation_terms) throws ClassNotFoundException, IOException {
		read_file("google", filelocation_google);
		read_file("yelp", filelocation_yelp);
		read_file("allTerms", filelocation_terms);
		get_vectors_mention_classes();
		
	}
	
	public void read_file(String dataset, String filelocation) throws IOException, ClassNotFoundException {
		if (dataset == "google") {
			File toRead=new File(filelocation);
		    FileInputStream fis=new FileInputStream(toRead);
		    ObjectInputStream ois=new ObjectInputStream(fis);
	        word_vec_google =(HashMap<String, double[]>) ois.readObject();

	        ois.close();
	        fis.close();	
		}
		if (dataset == "yelp") {
			File toRead_yelp=new File(filelocation);
		    FileInputStream fis_yelp=new FileInputStream(toRead_yelp);
		    ObjectInputStream ois_yelp =new ObjectInputStream(fis_yelp);
	        word_vec_yelp =(HashMap<String,double[]>)ois_yelp.readObject();
	        ois_yelp.close();
	        fis_yelp.close();	
		}
		if (dataset == "allTerms") {
			File toRead_terms=new File(filelocation);
		    FileInputStream fis_terms=new FileInputStream(toRead_terms);
		    ObjectInputStream ois_terms =new ObjectInputStream(fis_terms);
	        allTerms =(HashMap<String,String>)ois_terms.readObject();

	        ois_terms.close();
	        fis_terms.close();	
		}
	}
	
	
	public void get_vectors_mention_classes() {
		mention_words = Arrays.asList("ambience", "drinks","food","service","price","location","quality", "style", "options", "experience");
		for (String mention: mention_words) {
			mention_classes_vec.add(word_vec_yelp.get(mention));
		}
	}
	
	
	public double get_domain_similarity(double[] general_vec, double[] domain_vec) {	
		return (dotProduct(general_vec, domain_vec)/(getMagnitude(domain_vec) * getMagnitude(general_vec)));
	}
	
	// Mention classes are: Ambience, Drinks, Food, Service, Price, Restaurant, Location, Quality, Style, Options
	public double get_mention_class_similarity(double[] domain_vec) {
		double max = -1;
		for(double []vector : mention_classes_vec) {
			double similarity = (dotProduct(vector, domain_vec))/(getMagnitude(domain_vec) * getMagnitude(vector));
			if (similarity > max) {
				max = similarity;
			}
		}
		return max;
	}
	
	
	public double get_term_score(double[] general_vec, double[] domain_vec) {
		return 2 / ((get_domain_similarity(general_vec, domain_vec)+(1/get_mention_class_similarity(domain_vec))));
	}
	
	
	public void create_word_term_score() {
		for (Map.Entry<String, double[]> entry : word_vec_yelp.entrySet()) {
			if(word_vec_google.containsKey(entry.getKey())) {
				double term_score = get_term_score(word_vec_google.get(entry.getKey()), entry.getValue());
				if (term_scores.contains(term_score)) {
					
				}
				term_scores.add(term_score);
				term_scores_test.put(term_score, entry.getKey());
		    	//word_vec_yelp.put(entry.getKey(), w2vModel_yelp.getWordVector(entry.getKey()));
		    }
		}
		Collections.sort(term_scores, Collections.reverseOrder());
		System.out.println(term_scores_test);
	}
	
	public double create_threshold(int max_words, String lexical_class) throws IOException {
		double threshold_score = 0;
		int n_suggested = 0;
		int n_accepted = 0;
		double opt_treshold_score = Double.NEGATIVE_INFINITY;
		
		
		/**
		 * @param max_words: The maximum amount of words of a certain lexical class that are allowed in the ontology
		 * @param lexical_class: The lexical class that we will optimize the threshold for, acceptable entries are:
		 * "NN" = noun, "VB" = verb, "JJ" = adjective
		 * @returns The optimal threshold
		 */
		Scanner scan = new Scanner(System.in);
		JLanguageTool langTool = new JLanguageTool(new AmericanEnglish());
		for(Map.Entry<Double,String> entry : term_scores_test.entrySet()) {
			List<RuleMatch> matches = langTool.check(entry.getValue());
			if (matches.size()==0) {
				if (allTerms.get(entry.getValue()).contains(lexical_class) && !mention_words.contains(entry.getValue())) {
						System.out.println("Reject or accept: " +"{"+entry.getValue()+"}" +", This is a " + "{" +allTerms.get(entry.getValue())+"}"+ ", The TermScore is: " +entry.getKey() +", Press (y) to accept and (n) to reject.");
						String input = scan.nextLine();
						input = input.strip();
						n_suggested += 1;
						if (input.equals("y")) {
							n_accepted += 1;
							System.out.println("accepted!");
						}	
						else if (input.equals("n")) {
							System.out.println("Declined!");
						}
						
						else {
							boolean error = true;
							while (error) {
								System.out.println("Please enter a valid key");
								System.out.println("Reject or accept: " +"(" +entry.getValue()+")" +"?" +" Press (y) to accept and (n) to reject");
								String input_error = scan.nextLine();
								input_error = input_error.strip();
								if (input_error.equals("y")) {
									n_accepted += 1;
									error = false;
								}
								if (input_error.equals("n")) {
									error = false;
								}
								}
								
						}
						if (n_accepted > 0) {
							threshold_score = 2/((n_suggested/(double)n_accepted)+(max_words/(double)n_accepted));
							if (threshold_score > opt_treshold_score){
								opt_treshold_score = threshold_score;
								System.out.println("Optimal score: " + opt_treshold_score + " Number suggested: " + n_suggested + " Number accepted: " + n_accepted);
							}
						}
							
						if (n_suggested == max_words) {
							break;
						}	
					
				}
			}
			else
			{
				System.out.println("mispelled word found" + entry.getValue());
			}
			
		}
		System.out.println("Optimal threshold is " + opt_treshold_score);
		return opt_treshold_score;
	}
	
	
	
	public void create_term_list(double threshold_noun, double threshold_verb, double threshold_adj, int max_noun, int max_verb, int max_adj) throws IOException {	
		org.deeplearning4j.models.word2vec.Word2Vec w2vModel_yelp = WordVectorSerializer.readWord2VecModel(new File("C:\\Users\\Ruben\\PycharmProjects\\Word2Vec(2.0)\\w2v_yelp.bin"));
		Scanner scan = new Scanner(System.in);
		JLanguageTool langTool = new JLanguageTool(new AmericanEnglish());
		int accepted_noun = 0;
		int accepted_verb = 0;
		int accepted_adj = 0;
		
		for (Map.Entry<Double,String> entry : term_scores_test.entrySet() ) {
			if (!mention_words.contains(entry.getValue()) && !acceptedTerms.containsKey(entry.getValue())) {
				List<RuleMatch> matches = langTool.check(entry.getValue());
				if (matches.size() == 0) {
				
				if (allTerms.get(entry.getValue()).contains("NN") && entry.getKey() > threshold_noun && accepted_noun <= max_noun) {
					accepted_noun = ask_input(entry, scan, accepted_noun, "noun", w2vModel_yelp, langTool);
				}
				else if (allTerms.get(entry.getValue()).contains("VB") && entry.getKey() > threshold_verb && accepted_verb <= max_verb) {
					accepted_verb = ask_input(entry, scan, accepted_verb, "verb", w2vModel_yelp, langTool);
				}
				else if (allTerms.get(entry.getValue()).contains("JJ") && entry.getKey() > threshold_adj && accepted_adj <= max_adj) {
					accepted_adj = ask_input(entry, scan, accepted_adj, "adj", w2vModel_yelp, langTool);
				}
				}
				else
				{
					System.out.println("misspelled word found, skipping word!");
				}
			}
		}
		System.out.println(acceptedTerms);
		
	}

	private int ask_input(Map.Entry<Double,String> entry, Scanner scan, int to_increase, String type_word, org.deeplearning4j.models.word2vec.Word2Vec word2vec_yelp, JLanguageTool langTool) throws IOException {
		/*
		 * WordNetDatabase database = WordNetDatabase.getFileInstance(); Synset[]
		 * synsets = database.getSynsets("good");
		 * System.out.println(synsets[1].getWordForms());
		 */
		boolean error0 = true;
		String input;
		String input2 = null;
		String input3 = null;
		while(error0) {
			System.out.println("Reject or accept: " +"{"+entry.getValue()+"}" +", This is a " + "{" +allTerms.get(entry.getValue())+"}"+ ", The TermScore is: " +entry.getKey() +", Press (y) to accept and (n) to reject.");
			input = scan.nextLine();
			input = input.strip();
			// Accept the term
			if (input.equals("y")) {
				
				error0 = false;
				acceptedTerms.put(entry.getValue(), type_word);
				System.out.println("accepted!");
				to_increase += 1;
				
				// Check if is noun or verb
				if (type_word.equals("noun") || type_word.equals("verb")) {
					boolean error = true;
					// Ask user if it is an AspectMention or SentimentMention 
					
					while (error) {
						System.out.println("Please indicate whether this is a AspectMention (a) or a Sentiment Mention (s)");
						input2 = scan.nextLine();
						
						// If is aspectMention do this
						if (input2.equals("a")){
							System.out.println("Added to AspectMentionClass");
							aspect_mentions.put(entry.getValue(), type_word);
							
							error = false;
						}
						
						// If is SentimentMention do this
						else if (input2.equals("s")) {
							boolean loop = true;
							while(loop) {
								System.out.println("Is this a type 1,2 or 3 Sentiment Mention? Press (1) for type 1, (2) for type 2, (3) for type3");
								input3 = scan.nextLine();
								if (input3.equals("1")) {
									sentiment_mentions.put(entry.getValue(), 1);
									
									loop = false;
								}
								else if (input3.equals("2")) {
									sentiment_mentions.put(entry.getValue(), 2);

									loop = false;
								}
								else if (input3.equals("3")) {
									sentiment_mentions.put(entry.getValue(), 2);
									
									loop = false;
								}
								else {
									System.out.println("Please input a valid key");
								}
							}
							error = false;
						}
						
						// Non valid key loop again
						else {
							System.out.println("Please enter a valid key");
						}
					}
					
					// wordembedding based adding of similar words
					if (type_word.contentEquals("noun")) {
						Collection<String> similarity_list = word2vec_yelp.wordsNearest(entry.getValue(), 10);
						for (String similarity: similarity_list) {	
							if (!mention_words.contains(similarity) && !acceptedTerms.containsKey(similarity)) {
								List<RuleMatch> matches = langTool.check(similarity);
								if (word2vec_yelp.similarity(entry.getValue(), similarity) > 0.7 && matches.size() == 0) {
									if(input2.equals("a") && aspect_mentions.containsKey(similarity)==false) {
										System.out.println("Also added {"+similarity+"}");
										aspect_mentions.put(similarity, null);	
										acceptedTerms.put(similarity, type_word);

									}
									if(input2.equals("s") && sentiment_mentions.containsKey(similarity) == false) {
										if (input3.equals("1")) {
											System.out.println("Also added {"+similarity+"}");
											sentiment_mentions.put(similarity, 1);
											acceptedTerms.put(similarity, type_word);
										}
										else if (input3.equals("2")) {
											System.out.println("Also added {"+similarity+"}");
											sentiment_mentions.put(similarity, 2);
											acceptedTerms.put(similarity, type_word);

										}
										else if (input3.equals("3")) {
											System.out.println("Also added {"+similarity+"}");
											sentiment_mentions.put(similarity, 3);
											acceptedTerms.put(similarity, type_word);

									}		
								}
								
							}
							}
						}
					}
				}
				if (type_word.equals("adj"))	{
					boolean loop = true;
					while(loop) {
						System.out.println("Is this a type 1,2 or 3 Sentiment Mention? Press (1) for type 1, (2) for type 2, (3) for type3");
						input3 = scan.nextLine();
						if (input3.equals("1")) {
							sentiment_mentions.put(entry.getValue(), 1);
							
							loop = false;
						}
						else if (input3.equals("2")) {
							sentiment_mentions.put(entry.getValue(), 2);

							loop = false;
						}
						else if (input3.equals("3")) {
							sentiment_mentions.put(entry.getValue(), 3);
							loop = false;
						}
						else {
							System.out.println("Please input a valid key");
						}
					}
				}
			}
			// Decline the term
			else if (input.equals("n")) {
				System.out.println("Declined!");
				error0 = false;
			}
			else
			{
				System.out.println("Please enter a valid key");
			}
		}
	return to_increase;
	}
	
	public void save_to_file_map(Map<String,Integer> terms, String filelocation) {
		System.out.println("saving file..");
	    try {
	        File fileOne=new File(filelocation);
	        FileOutputStream fos=new FileOutputStream(fileOne);
	        ObjectOutputStream oos=new ObjectOutputStream(fos);

	        oos.writeObject(terms);
	        oos.flush();
	        oos.close();
	        fos.close();
	    } catch(Exception e) {}
	}
	
	public void save_to_file_map_string(Map<String,String> terms, String filelocation) {
		System.out.println("saving file..");
	    try {
	        File fileOne=new File(filelocation);
	        FileOutputStream fos=new FileOutputStream(fileOne);
	        ObjectOutputStream oos=new ObjectOutputStream(fos);

	        oos.writeObject(terms);
	        oos.flush();
	        oos.close();
	        fos.close();
	    } catch(Exception e) {}
	}
	
	
	/*
	 * public void save_to_file_set(Set<String> acceptedTerms, String path) throws
	 * Exception { PrintWriter pw = null; try { pw = new PrintWriter( new
	 * OutputStreamWriter(new FileOutputStream(path), "UTF-8")); for (String s :
	 * acceptedTerms) { pw.println(s); } pw.flush(); } finally { pw.close(); } }
	 * 
	 * public void load_file() throws IOException { Set<String> allAcceptedTerms =
	 * new HashSet<String>();
	 * 
	 * //CHANGE TO YOUR FILE LOCATION File fileDir = new
	 * File("E:\\Output_selected_terms\\all_terms");
	 * 
	 * BufferedReader in = new BufferedReader( new InputStreamReader(new
	 * FileInputStream(fileDir), "UTF-8"));
	 * 
	 * String str;
	 * 
	 * while ((str = in.readLine()) != null) { allAcceptedTerms.add(str); }
	 * 
	 * in.close(); System.out.println(allAcceptedTerms); }
	 */
	
	public static void main(String args[]) throws Exception {
		TermSelectionAlgo term_select = new TermSelectionAlgo( "E://google_wordvec", "E://yelp_wordvec", "E:\\OutputTerms\\Output_stanford_hashmap");
		term_select.create_word_term_score();
		System.out.println("doing thresholds");
		//double threshold_noun = term_select.create_threshold(100, "NN");
		//double threshold_verb = term_select.create_threshold(15, "VB");
		//double threshold_adj = term_select.create_threshold(80, "JJ");
		//term_select.create_term_list(0.84, threshold_verb, threshold_adj, 100, 80, 80);
		term_select.create_term_list(0.84, 0.8, 0.915, 100, 20, 80);
		term_select.save_to_file_map_string(term_select.aspect_mentions, "E:\\Output_selected_terms\\aspect_mentions");
		term_select.save_to_file_map(term_select.sentiment_mentions, "E:\\Output_selected_terms\\sentiment_mentions");
		term_select.save_to_file_map_string(term_select.acceptedTerms, "E:\\Output_selected_terms\\all_accepted_terms");
	}
	
	static class DescOrder implements Comparator<Double>{
		@Override
		public int compare(Double o1, Double o2) {
			return o2.compareTo(o1);
		}
	}
	
	public static double dotProduct(double[] a, double[] b) {
		double sum = 0;
		for (int i = 0; i < a.length; i++) {
			sum += a[i] * b[i];
		}
		return sum;
	}
	public static double getMagnitude(double[] vec_1) {
		double magnitude = 0;
		for (int j = 0; j < vec_1.length; j++) {
			magnitude += Math.pow(vec_1[j],2);
		}
		return Math.sqrt(magnitude);
	}
}

