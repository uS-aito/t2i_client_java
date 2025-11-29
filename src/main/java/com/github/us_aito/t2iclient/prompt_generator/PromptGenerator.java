package com.github.us_aito.t2iclient.prompt_generator;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Random;

public class PromptGenerator {
  /**
   * プロンプトをレンダリングするメソッド
   * プロンプト内の<key>をもとにlibraryの要素で置換を行う
   * countがライブラリの各要素数より少ない場合、重複なくレンダリングを行う
   * countがライブラリの各要素数より多い場合は、可能な限り重複を避けてレンダリングを行う
   * @param prompt
   * @param library
   * @param count
   * @return
   */
  public static List<String> generatePrompts(String prompt, Map<String, List<String>> library, int count) {
    Map<String, List<String>> localLibrary = library.entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> new ArrayList<>(e.getValue())
      ));

    Pattern pattern = Pattern.compile("<(.*?)>");
    Matcher matcher = pattern.matcher(prompt);
    List<String> keys = new ArrayList<>();
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }

    /*
    * localLibraryからkeyに対応する値を取得する
    * そのうちの一つを選び、promptを置換する
    * 選んだ値をlocalLibraryのkeyの値から削除する
    */
    List<String> renderedPrompts = new ArrayList<>();
    for(int i = 0; i < count; i++) {
      String resultPrompt = prompt;
      for (String key : keys) {
        if (localLibrary.get(key).isEmpty()) {
          // ライブラリの要素が尽きた場合は再度初期化する
          localLibrary.put(key, new ArrayList<>(library.get(key)));
        }
        int index = new Random().nextInt(localLibrary.get(key).size());
        String value = localLibrary.get(key).get(index);
        resultPrompt = resultPrompt.replace("<" + key + ">", value);
        localLibrary.get(key).remove(index);
      }
      renderedPrompts.add(resultPrompt);
    }

    return renderedPrompts;
  }

  public static void main(String[] args) {
    String prompt = "A <facial_expression> person <pose> in the park.";
    Map<String, List<String>> library = Map.of(
      "facial_expression", List.of("smile", "serious", "angry"),
      "pose", List.of("standing", "sitting", "jumping")
    );
    int count = 5;

    List<String> renderedPrompts = PromptGenerator.generatePrompts(prompt, library, count);
    renderedPrompts.forEach(System.out::println);
  }
}
