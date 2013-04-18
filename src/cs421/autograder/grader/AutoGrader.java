package cs421.autograder.grader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

public class AutoGrader {
	
	private MaxentTagger stanfordTagger = null;
	private POSModel opennlpModel = null;
	private POSTaggerME opennlpTagger = null;	
	private ParserModel pmodel = null; 
	private InputStream parsermodelIn = null;
	private InputStream smodel = null;
		
	public ArrayList<PosTag> getStanfordPosTags(String text){
	
		ArrayList<PosTag> posTags = new ArrayList<PosTag>();
		String word = "";
		String posText = "";
		int separatorIndex = 0;
		
		try {
			
			if(stanfordTagger == null){
				
				//MaxentTagger stanfordTagger = new MaxentTagger(System.getProperty("user.dir") + "/Models/bidirectional-distsim-wsj-0-18.tagger");
				stanfordTagger = new MaxentTagger(System.getProperty("user.dir") + "/Models/left3words-wsj-0-18.tagger");
			}			
			
			String tagged = stanfordTagger.tagString(text);
			//System.out.println(tagged);
			for(String wordTag : tagged.split(" ")){
				
				separatorIndex = wordTag.indexOf('/');
				word = wordTag.substring(0, separatorIndex);
				posText = wordTag.substring(separatorIndex + 1);
								
				posTags.add(new PosTag(word, getPOS(posText)));
			}	
			
			return posTags;
		}
		catch (IOException e) {
			e.printStackTrace();
			System.out.println("[Error] Stanford POS tagger model loading failed");
			return new ArrayList<PosTag>();
		}
		catch (Exception e){
			e.printStackTrace();
			System.out.println("[Error] Part of speech tagging error");
			return new ArrayList<PosTag>();
		}
	}
	
	public ArrayList<PosTag> getOpennlpPosTags(String[] textArray){
		
		InputStream modelIn = null;
		String[] tags;
		ArrayList<PosTag> posTags = new ArrayList<PosTag>();

		try {
			
			if(opennlpModel == null || opennlpTagger == null){
				modelIn = new FileInputStream(System.getProperty("user.dir") + "/Models/en-pos-maxent.bin");
				opennlpModel = new POSModel(modelIn);
				
				opennlpTagger = new POSTaggerME(opennlpModel);
			}
			
			
			tags = opennlpTagger.tag(textArray);
			
			if(textArray.length != tags.length)
				throw new Exception();
			
			for(int i=0; i < tags.length; i++){

				posTags.add(new PosTag(textArray[i], getPOS(tags[i])));
			}
			
			return posTags;
		}
		catch (IOException e) {
			e.printStackTrace();
			System.out.println("[Error] OpenNLP POS tagger model loading failed");
			return new ArrayList<PosTag>();
		}
		catch (Exception e){
			e.printStackTrace();
			System.out.println("[Error] Part of speech tagging error");
			return new ArrayList<PosTag>();
		}
		finally {
			if (modelIn != null) {
				
				try {
		    		modelIn.close();
				}
				catch (IOException e) {	}
			}
		}
	}	
	
	public Parse getParseTree(String sentence){
		
		try {	
		
			if(pmodel == null || parsermodelIn == null){
								
				parsermodelIn = new FileInputStream(System.getProperty("user.dir") + "/Models/en-parser-chunking.bin");			
				pmodel = new ParserModel(parsermodelIn);				
			}
			
			Parser parser = ParserFactory.create(pmodel);
						
			Parse topParses[] = ParserTool.parseLine(sentence, parser, 1);	
			
			//System.out.println(sentence);
			//topParses[0].show();
			
			return topParses[0];
		}
		catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		finally {
			if (parsermodelIn != null) {
				try {
					parsermodelIn.close();
				}
				catch (IOException e) {
				}
			}	
		}
	}
	
	/**
	 * grade the essay text based on punctuation, capitalization, finite verb hypothesis
	 * @param Essay object
	 * @author pavan
	 */
	
	public void gradeEssayLength(Essay essay){
		
		int length = 0;
		
		int lines = essay.getSentences().size();
		int verbs = 0;
		int sentencebreaks = 0;
		int localverbs = 0;
		int conjunctions = 0;
		
		ArrayList<PosTag> tags = new ArrayList<PosTag>();
		
		for(int i=0; i < essay.getSentences().size(); i++){
			
			localverbs = 0;
			conjunctions = 0;
			
			if(essay.getSentences().get(i).equals(""))continue;
			
			tags = essay.getPosTags().get(i);
			
			for(PosTag pos : tags){
				
				if(pos.getPartOfSpeech() == PartOfSpeech.VB || pos.getPartOfSpeech() == PartOfSpeech.VBD ||
						pos.getPartOfSpeech() == PartOfSpeech.VBG || pos.getPartOfSpeech() == PartOfSpeech.VBN ||
						pos.getPartOfSpeech() == PartOfSpeech.VBP || pos.getPartOfSpeech() == PartOfSpeech.VBZ){
					
					localverbs = localverbs + 1;
				}
				
				if(pos.getPartOfSpeech() == PartOfSpeech.CC){
					conjunctions = conjunctions + 1;
				}	
				
				if(pos.getPartOfSpeech() == PartOfSpeech.DOT)
					sentencebreaks = sentencebreaks + 1;
			}
			
			if(localverbs > conjunctions)
				verbs = verbs + (localverbs - conjunctions);
			else 
				verbs = verbs + localverbs;							
		}
		
		length = (lines > verbs)?(lines > sentencebreaks)?(lines):(sentencebreaks):(verbs > sentencebreaks)?(verbs):(sentencebreaks);
		
		if(length >= 6)
			essay.getEssayScore().setEssayLengthScore(5);
		else if(length == 5)
			essay.getEssayScore().setEssayLengthScore(4);
		else if(length == 4)
			essay.getEssayScore().setEssayLengthScore(3);
		else if(length == 3)
			essay.getEssayScore().setEssayLengthScore(2);
		else if(length < 3 && length > 0)
			essay.getEssayScore().setEssayLengthScore(1);
		else 
			essay.getEssayScore().setEssayLengthScore(0);
		
		//return essay;
	}
	
	public PartOfSpeech getPOS(String posText){
	    
    	PartOfSpeech returnPos = PartOfSpeech.FW;
    	
    	try{
    	
    		if(posText.equals("."))
				returnPos = PartOfSpeech.DOT;
			else if(posText.equals(","))
				returnPos = PartOfSpeech.COMMA;
			else if(posText.equals(":"))
				returnPos = PartOfSpeech.COLON;
			else if(posText.equals("$"))
				returnPos = PartOfSpeech.DOLLAR;
			else if(posText.equals("#"))
				returnPos = PartOfSpeech.POUND;
			else if(posText.equals("("))
				returnPos = PartOfSpeech.LBRACKET;
			else if(posText.equals(")"))
				returnPos = PartOfSpeech.RBRACKET;
			else if(posText.equals("``"))
				returnPos = PartOfSpeech.LQUOTE;
			else if(posText.equals("''"))
				returnPos = PartOfSpeech.RQUOTE;
			else
			{
				for(PartOfSpeech pos : PartOfSpeech.values()){
	    						
					if(PartOfSpeech.valueOf(posText).equals(pos))
						returnPos = pos;				
				}
			}
			
			return returnPos;
    	}
		catch(IllegalArgumentException e){
			e.printStackTrace();
			returnPos = PartOfSpeech.FW;
			System.out.println("[Error] No such part of speech, using default FW");
			return returnPos;
		}
		catch(NullPointerException e){
			e.printStackTrace();
			returnPos = PartOfSpeech.FW;
			System.out.println("[Error] No part of speech, using default FW");
			return returnPos;
		}
    }

	public void segmentEssay(Essay essay){
		
		try {
			
			//if(smodel == null){
				
				smodel = new FileInputStream(System.getProperty("user.dir") + "/Models/en-sent.bin");	
			//}
			
			SentenceModel model = new SentenceModel(smodel);
		  
			SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);
		  
			ArrayList<String> newsent = new ArrayList<String>();
		  
			for(int i=0; i<essay.getSentences().size(); i++){
		  
				newsent.addAll(Arrays.asList(sentenceDetector.sentDetect(essay.getSentences().get(i))));		  
			}
		  
		  essay.setDetectedSentences(newsent);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			if (smodel != null) {
				try {
					//smodel = null;
					smodel.close();
				}
				catch (IOException e) {
				}
			}
		}
	}
	
	public void gradeSyntax(Essay essay) {
		
		ArrayList<Parse> parse = essay.getParsedSentences();	
		
		PosTag subject = new PosTag(null,null);
		PosTag mainVerb = new PosTag(null,null);
		Parse node;
		Parse tag;
		int sCount = parse.size();
		int berrorcount = 0;
		int cerrorcount = 0;
		
		ArrayList<String> nouns = new ArrayList<String>();
		nouns.add(PartOfSpeech.NN.toString());
		nouns.add(PartOfSpeech.NNP.toString());
		nouns.add(PartOfSpeech.NNPS.toString());
		nouns.add(PartOfSpeech.NNS.toString());
		
		ArrayList<String> pronouns = new ArrayList<String>();
		pronouns.add(PartOfSpeech.PRP.toString());
		pronouns.add(PartOfSpeech.PRP$.toString());
		
		ArrayList<String> verbs = new ArrayList<String>();		
		verbs.add(PartOfSpeech.VB.toString());
		verbs.add(PartOfSpeech.VBD.toString());
		verbs.add(PartOfSpeech.VBG.toString());
		verbs.add(PartOfSpeech.VBN.toString());
		verbs.add(PartOfSpeech.VBP.toString());
		verbs.add(PartOfSpeech.VBZ.toString());
		
		for(int i=0; i<parse.size();i++){	
			
			subject = new PosTag(null,null);
			mainVerb = new PosTag(null,null);
			//parse.get(i).show();
			
			node = BFS(parse.get(i),PartOfSpeech.NP.toString());	
						
			if(node != null){
				
				tag = BFS(node,nouns);
				
				if(tag == null)
					tag = BFS(node, pronouns);
				
				if(tag != null)
					subject = new PosTag(tag.toString(), getPOS(tag.getType()));				
			}
				
			node = BFS(parse.get(i),PartOfSpeech.VP.toString());
			
			if(node != null){
				
				tag = BFS(node,verbs);
				
				if(tag != null)
					mainVerb = new PosTag(tag.toString(), getPOS(tag.getType()));				
			}
			
			
			//System.out.println(mainVerb.getString());
			//System.out.println(subject.getString());
			
			//evaluation 1b
			if(PartOfSpeech.getPersonType(subject.getPartOfSpeech(),subject.getString())
					!= PartOfSpeech.getPersonType(mainVerb.getPartOfSpeech(), mainVerb.getString())){
				//System.out.println(mainVerb.getString());
				//System.out.println(subject.getString());
				berrorcount = berrorcount + 1;
			}
			
			//evaluation 1c
			if(mainVerb.getString() == null || mainVerb.getPartOfSpeech() == null)
				cerrorcount = cerrorcount +1;
			
		    essay.getEssayScore().setSubjectVerbAgreementScore(essay.getEssayScore().computeScore(berrorcount,sCount));
		    essay.getEssayScore().setSubjectVerbAgreementScore(essay.getEssayScore().computeScore(cerrorcount,sCount));
		}
		
		//System.out.println(berrorcount + "/" + sCount);
	}
		
	public Parse BFS(Parse graph,String searchText){
		
		Queue<Parse> queue = new LinkedList<Parse>();
				
		queue.add(graph);
		
		while(!queue.isEmpty()){
			
			Parse node = (Parse)queue.remove();
						
			if(node.getType().equals(searchText)){
				return node;
			}
			
			for(int i=0; i<node.getChildCount();i++ ){	
				
				queue.add(node.getChildren()[i]);
			}			
		}
		
		return null;
	}
	
	public Parse BFS(Parse graph,ArrayList<String> searchText){
		
		Queue<Parse> queue = new LinkedList<Parse>();
				
		queue.add(graph);
		
		while(!queue.isEmpty()){
			
			Parse node = (Parse)queue.remove();
						
			if(searchText.contains(node.getType())){
				return node;
			}
			
			for(int i=0; i<node.getChildCount();i++ ){	
				
				queue.add(node.getChildren()[i]);
			}			
		}
		
		return null;
	}
}
