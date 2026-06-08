package com.shauiqiu.eklose

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EkwingAnswerReaderParserTest {
    @Test
    fun parseReleaseStyleFallbackQuestionAndAnswerKeys() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "records": [
                    {
                      "question_text": "Release style question",
                      "correctAnswer": "Correct answer"
                    },
                    {
                      "stem": "Reference style question",
                      "reference_answer": ["Reference answer"]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(2, questions.size)
        assertEquals("Release style question", questions[0].question)
        assertEquals("Correct answer", questions[0].answer)
        assertEquals("Reference style question", questions[1].question)
        assertEquals("Reference answer", questions[1].answer)
    }

    @Test
    fun parseAnswerObjectContentFallback() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "question": "Content object answer",
                  "answer": {"content": "Content answer"}
                }
                """.trimIndent()
            )
        )

        assertEquals("Content object answer", questions.single().question)
        assertEquals("Content answer", questions.single().answer)
    }

    @Test
    fun parseAnswerObjectAlternateDisplayTextFields() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "answers": [
                    {
                      "question": "Value answer question",
                      "answer": {"value": "Value answer"}
                    },
                    {
                      "question": "Option answer question",
                      "answer": {"optionText": "Option answer"}
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(2, questions.size)
        assertEquals("Value answer", questions[0].answer)
        assertEquals("Option answer", questions[1].answer)
    }

    @Test
    fun parseRepairsMojibakeQuestionAndAnswerText() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "question": "${mojibake("你好")}",
                  "answer": "${mojibake("答案")}"
                }
                """.trimIndent()
            )
        )

        assertEquals("你好", questions.single().question)
        assertEquals("答案", questions.single().answer)
    }

    @Test
    fun parseCleansHtmlTagsAndEntitiesFromDisplayText() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "question": "<p>Hello&nbsp;world</p>",
                  "answer": "<b>Answer</b><br>Line2"
                }
                """.trimIndent()
            )
        )

        assertEquals("Hello world", questions.single().question)
        assertEquals("Answer\nLine2", questions.single().answer)
    }

    @Test
    fun parseDecodesUnicodeEscapedDisplayTextValues() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "question": "\\u4f60\\u597d",
                  "answer": "\\u7b54\\u6848"
                }
                """.trimIndent()
            )
        )

        assertEquals("你好", questions.single().question)
        assertEquals("答案", questions.single().answer)
    }

    @Test
    fun parseHomeworkAnsBlock() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "ans": {
                    "txt": "Listen and answer",
                    "answers": [
                      {"text": "Question one", "answer": ["Answer one"]},
                      {"sentence": "Question two", "right_answer": "Answer two"}
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(2, questions.size)
        assertEquals("Question one", questions[0].question)
        assertEquals("Answer one", questions[0].answer)
        assertEquals("Answer two", questions[1].answer)
    }

    @Test
    fun parseHomeworkAnsBlockUsesReleaseStyleFallbackKeys() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "ans": {
                    "txt": "Fallback root",
                    "answers": [
                      {
                        "question_text": "Homework release question",
                        "correctAnswer": "Homework correct answer"
                      },
                      {
                        "stem": "Homework standard question",
                        "standardAnswer": {"content": "Homework standard answer"}
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(2, questions.size)
        assertEquals("Homework release question", questions[0].question)
        assertEquals("Homework correct answer", questions[0].answer)
        assertEquals("Homework standard question", questions[1].question)
        assertEquals("Homework standard answer", questions[1].answer)
    }

    @Test
    fun parseHomeworkAnsBlockOnlyKeepsStandardAnswer() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "ans": {
                    "txt": "Read aloud",
                    "answers": [
                      {
                        "sentence": "Hello world",
                        "right_answer": "Hello world",
                        "user_answer": "Hello word",
                        "score": "87",
                        "audio": "https://example.test/a.mp3",
                        "accuracy": "92"
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals("Hello world", questions.single().question)
        assertEquals("Hello world", questions.single().answer)
    }

    @Test
    fun parseHomeworkAnsBlockDoesNotTreatResultDetailsAsStandardAnswer() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "ans": {
                    "answers": [
                      {
                        "sentence": "Result-only item",
                        "user_answer": "Student result",
                        "score": "76",
                        "audio": "https://example.test/result.mp3"
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals("Result-only item", questions.single().question)
        assertEquals("", questions.single().answer)
    }

    @Test
    fun parseHomeworkTopLevelAnswerArrays() {
        val answersQuestions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "answers": [
                    {"question": "Top answers question", "answer": "Top answers answer"}
                  ]
                }
                """.trimIndent()
            )
        )
        assertEquals(1, answersQuestions.size)
        assertEquals("Top answers question", answersQuestions.single().question)
        assertEquals("Top answers answer", answersQuestions.single().answer)

        val dataQuestions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "data": [
                    {"question": "Data question", "right_answer": "Data answer"}
                  ]
                }
                """.trimIndent()
            )
        )
        assertEquals(1, dataQuestions.size)
        assertEquals("Data question", dataQuestions.single().question)
        assertEquals("Data answer", dataQuestions.single().answer)
    }

    @Test
    fun parseHomeworkScalarAnswerArrays() {
        val topLevel = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "answers": ["Answer A", "Answer B"]
                }
                """.trimIndent()
            )
        )
        assertEquals(2, topLevel.size)
        assertEquals("题目 1", topLevel[0].question)
        assertEquals("Answer A", topLevel[0].answer)
        assertEquals("题目 2", topLevel[1].question)
        assertEquals("Answer B", topLevel[1].answer)

        val ansBlock = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "ans": {
                    "txt": "Shared question",
                    "answers": ["Shared answer"]
                  }
                }
                """.trimIndent()
            )
        )
        assertEquals("Shared question", ansBlock.single().question)
        assertEquals("Shared answer", ansBlock.single().answer)
    }

    @Test
    fun parseHomeworkUserAnswerFieldsAreNotStandardAnswers() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "answers": [
                    {"question": "Answer content question", "answer_content": "Answer content value"},
                    {"question": "User ans question", "user_ans": "User ans value"}
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(2, questions.size)
        assertEquals("", questions[0].answer)
        assertEquals("", questions[1].answer)
    }

    @Test
    fun parseHomeworkNestedAnswerArrays() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "data": {
                    "result": {
                      "answers": [
                        {"question": "Nested question", "answer": "Nested answer"}
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(1, questions.size)
        assertEquals("Nested question", questions.single().question)
        assertEquals("Nested answer", questions.single().answer)
    }

    @Test
    fun parseHomeworkQuestionListAndAlternateAnswerKeys() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "questionList": [
                    {
                      "prompt": "Question list prompt",
                      "rightAns": "Right answer value"
                    },
                    {
                      "body": "Answer list body",
                      "correctText": ["Correct text value"]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(2, questions.size)
        assertEquals("Question list prompt", questions[0].question)
        assertEquals("Right answer value", questions[0].answer)
        assertEquals("Answer list body", questions[1].question)
        assertEquals("Correct text value", questions[1].answer)
    }

    @Test
    fun parseHomeworkQuestionOnlyItemsDoNotUseRawJsonAsAnswer() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "answers": [
                    {
                      "sentence": "Question-only sentence",
                      "audio": "https://example.test/question.mp3"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals("Question-only sentence", questions.single().question)
        assertEquals("", questions.single().answer)
    }

    @Test
    fun parseAnswerFieldsFromDetailItemItself() {
        val questions = parseHomeworkAnswers(
            JSONObject(
                """
                {
                  "id": "detail-item-1",
                  "type_name": "详情自带答案",
                  "title": "Detail item question",
                  "right_answer": "Detail item answer"
                }
                """.trimIndent()
            )
        )

        assertEquals(1, questions.size)
        assertEquals("Detail item question", questions.single().question)
        assertEquals("Detail item answer", questions.single().answer)
    }

    @Test
    fun parseContentQuestionsFromEmbeddedJsonHtml() {
        val questions = EkwingAnswerParser.parseContentQuestions(
            """
            <!doctype html>
            <html>
              <body>
                <script>
                  window.__EKWING_HOMEWORK__ = {
                    "data": {
                      "items": [
                        {
                          "prompt": "Embedded content question",
                          "standardAnswer": "Embedded content answer"
                        }
                      ]
                    }
                  };
                </script>
              </body>
            </html>
            """.trimIndent()
        )

        assertEquals(1, questions.size)
        assertEquals("Embedded content question", questions.single().question)
        assertEquals("Embedded content answer", questions.single().answer)
    }

    @Test
    fun parseContentQuestionsFromHtmlEscapedJson() {
        val questions = EkwingAnswerParser.parseContentQuestions(
            """
            <script>
              window.__DATA__ = {&quot;items&quot;:[{&quot;prompt&quot;:&quot;Escaped question&quot;,&quot;standardAnswer&quot;:&quot;Escaped answer&quot;}]};
            </script>
            """.trimIndent()
        )

        assertEquals(1, questions.size)
        assertEquals("Escaped question", questions.single().question)
        assertEquals("Escaped answer", questions.single().answer)
    }

    @Test
    fun parseContentQuestionsDoesNotTreatQuestionTextAsAnswer() {
        val questions = EkwingAnswerParser.parseContentQuestions(
            JSONObject(
                """
                {
                  "items": [
                    {
                      "sentence": "Read this sentence aloud",
                      "audio": "https://example.test/question.mp3"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals("Read this sentence aloud", questions.single().question)
        assertEquals("", questions.single().answer)
    }

    @Test
    fun parseContentQuestionsStillReadsEmbeddedStandardAnswer() {
        val questions = EkwingAnswerParser.parseContentQuestions(
            JSONObject(
                """
                {
                  "items": [
                    {
                      "sentence": "Choose the right sentence",
                      "standardAnswer": {
                        "text": "Correct sentence"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals("Choose the right sentence", questions.single().question)
        assertEquals("Correct sentence", questions.single().answer)
    }

    @Test
    fun parseExamModelScoreOnlyOutputsStandardAnswer() {
        val questions = EkwingAnswerParser.parseExamAnswerQuestions(
            JSONObject(
                """
                {
                  "data": {
                    "model_info": {
                      "model_type_name": "回答问题",
                      "ques_list": [
                        {
                          "title_text": "Current exam question",
                          "answer": ["Correct exam answer"],
                          "user_ans": "Student answer",
                          "score": "80"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(1, questions.size)
        assertEquals("Current exam question", questions.single().question)
        assertEquals("Correct exam answer", questions.single().answer)
    }

    @Test
    fun parseExamModelScoreDoesNotUseStudentAnswerAsStandardAnswer() {
        val questions = EkwingAnswerParser.parseExamAnswerQuestions(
            JSONObject(
                """
                {
                  "data": {
                    "model_info": {
                      "model_type_name": "回答问题",
                      "ques_list": [
                        {
                          "title_text": "Historical exam question",
                          "user_ans": "Student only answer",
                          "score": "76"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(1, questions.size)
        assertEquals("Historical exam question", questions.single().question)
        assertEquals("", questions.single().answer)
    }

    @Test
    fun parseExamReportUsesReferenceTextNotSpokenResultDetails() {
        val questions = EkwingAnswerParser.parseExamAnswerQuestions(
            JSONObject(
                """
                {
                  "self_info": {"self_id": "exam-1"},
                  "ans_info": {
                    "content": [
                      {
                        "ques_list": [
                          {
                            "title": "Read aloud",
                            "refText": ["Correct sentence"],
                            "hypothesis": "Student spoken sentence",
                            "score": "90"
                          }
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(1, questions.size)
        assertEquals("Read aloud", questions.single().question)
        assertEquals("Correct sentence", questions.single().answer)
    }

    @Test
    fun examAnswerQuestionsPreferModelScoreStandardAnswersOverScoreReport() {
        val scoreInfo = JSONObject(
            """
            {
              "ans_info": {
                "content": [
                  {
                    "ques_list": [
                      {
                        "title": "Historical report question",
                        "user_ans": "Student spoken sentence",
                        "hypothesis": "Student spoken sentence",
                        "score": "90"
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        )
        val modelScoreInfos = JSONArray(
            """
            [
              {
                "data": {
                  "model_info": {
                    "model_type_name": "回答问题",
                    "ques_list": [
                      {
                        "title_text": "Historical model question",
                        "answer": ["Correct model answer"],
                        "user_ans": "Student spoken sentence",
                        "score": "90"
                      }
                    ]
                  }
                }
              }
            ]
            """.trimIndent()
        )

        val questions = examAnswerQuestions(scoreInfo, modelScoreInfos)

        assertEquals(1, questions.size)
        assertEquals("Historical model question", questions.single().question)
        assertEquals("Correct model answer", questions.single().answer)
    }

    @Test
    fun parseJsonExamAnswersMatchesPythonModelScoreRawShape() {
        val questions = parseJsonExamAnswers(
            JSONArray(
                """
                [
                  {
                    "ok": true,
                    "request": {
                      "url": "https://mapi.ekwing.com/student/exam/getmodelscoreinfo?self_id=self-1&model_id=model-7"
                    },
                    "path": "/student/exam/getmodelscoreinfo",
                    "payload": {
                      "self_id": "self-1",
                      "model_id": "model-7"
                    },
                    "data": {
                      "model_base_info": {
                        "title_info": {"title": "听后回答"}
                      },
                      "model_info": {
                        "model_type": "8",
                        "ques_list": [
                          {
                            "qid": "q1",
                            "question": "What did Tom buy?",
                            "answer": [{"text": "A book"}],
                            "user_ans": "A pen"
                          },
                          {
                            "qid": "q2",
                            "title": "Where is Tom?",
                            "answer": ["In the library"]
                          }
                        ]
                      }
                    }
                  }
                ]
                """.trimIndent()
            )
        )

        assertEquals(2, questions.size)
        assertEquals("What did Tom buy?", questions[0].question)
        assertEquals("A book", questions[0].answer)
        assertEquals("Where is Tom?", questions[1].question)
        assertEquals("In the library", questions[1].answer)
    }

    @Test
    fun parseJsonExamAnswersSkipsModelScoreItemsWithoutStandardAnswers() {
        val questions = parseJsonExamAnswers(
            JSONArray(
                """
                [
                  {
                    "ok": true,
                    "data": {
                      "model_info": {
                        "model_type": "8",
                        "ques_list": [
                          {
                            "title_text": "",
                            "user_ans": "student metadata",
                            "score": "0"
                          },
                          {
                            "title_text": "Placeholder question",
                            "answer": [],
                            "user_ans": "student answer"
                          },
                          {
                            "title_text": "Real question",
                            "answer": ["Real standard answer"]
                          }
                        ]
                      }
                    }
                  }
                ]
                """.trimIndent()
            )
        )

        assertEquals(1, questions.size)
        assertEquals("Real question", questions.single().question)
        assertEquals("Real standard answer", questions.single().answer)
    }

    @Test
    fun parseJsonExamAnswersAcceptsResultRootModelScoreInfos() {
        val questions = parseJsonExamAnswers(
            JSONObject(
                """
                {
                  "model_score_infos": [
                    {
                      "ok": true,
                      "data": {
                        "model_info": {
                          "model_type": "6",
                          "answer_tip": "Retell the information",
                          "answer": {"content": "Reference retelling"}
                        }
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(1, questions.size)
        assertEquals("Retell the information", questions.single().question)
        assertEquals("Reference retelling", questions.single().answer)
    }

    @Test
    fun extractScoreJsonFromHtmlJsonParseThenParsesExamAnswers() {
        val json = """
            {
              "model_info": {
                "ques_list": [
                  {
                    "title_text": "HTML wrapped question",
                    "answer": ["HTML wrapped answer"],
                    "user_ans": "Student answer"
                  }
                ]
              }
            }
        """.trimIndent()
        val html = "<html><script>window.__data = JSON.parse('${json.replace("\\", "\\\\").replace("'", "\\'")}');</script></html>"

        val extracted = extractScoreJsonFromText(html)
        val questions = parseJsonExamAnswers(extracted)

        assertEquals(1, questions.size)
        assertEquals("HTML wrapped question", questions.single().question)
        assertEquals("HTML wrapped answer", questions.single().answer)
    }

    @Test
    fun extractModelScoreRequestsMatchesReleaseScoreInfoLinks() {
        val requests = extractModelScoreRequests(
            JSONObject(
                """
                {
                  "ans_info": {
                    "content": [
                      {
                        "ques_list": [
                          {
                            "url": "https://mapi.ekwing.com/student/exam/getmodelscoreinfo?self_id=self-1&model_id=model-7",
                            "status": "done"
                          }
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(1, requests.size)
        assertEquals("/student/exam/getmodelscoreinfo", requests.single().optString("path"))
        assertEquals("self-1", requests.single().optString("self_id"))
        assertEquals("model-7", requests.single().optString("model_id"))
    }

    @Test
    fun parseHomeworkAnswersFromUrlEncodedJson() {
        val questions = parseHomeworkAnswers(
            "%7B%22answers%22%3A%5B%7B%22question%22%3A%22Url%20question%22%2C%22answer%22%3A%22Url%20answer%22%7D%5D%7D"
        )

        assertEquals(1, questions.size)
        assertEquals("Url question", questions.single().question)
        assertEquals("Url answer", questions.single().answer)
    }

    @Test
    fun parseHomeworkAnswersFromUnicodeEscapedJson() {
        val questions = parseHomeworkAnswers(
            """{\u0022answers\u0022:[{\u0022question\u0022:\u0022Unicode question\u0022,\u0022answer\u0022:\u0022Unicode answer\u0022}]}"""
        )

        assertEquals(1, questions.size)
        assertEquals("Unicode question", questions.single().question)
        assertEquals("Unicode answer", questions.single().answer)
    }

    @Test
    fun studyCenterTasksAcceptObjectWrappedLists() {
        val tasks = EkwingTaskListParser.studyCenterExamTasks(
            JSONObject(
                """
                {
                  "list": [
                    {"type": "exam", "id": "exam-1"},
                    {"type": "hw", "id": "hw-1"},
                    {"type": "train", "id": "train-1"},
                    {"type": "notice", "id": "notice-1"}
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("exam-1"), tasks.map { it.optString("id") })
    }

    @Test
    fun studyCenterTasksAcceptCaseInsensitiveExamType() {
        val tasks = EkwingTaskListParser.studyCenterExamTasks(
            JSONObject(
                """
                {
                  "list": [
                    {"type": " EXAM ", "id": "exam-upper"},
                    {"type": "hw", "id": "hw-1"}
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("exam-upper"), tasks.map { it.optString("id") })
    }

    @Test
    fun studyCenterTasksCollectNestedArrays() {
        val tasks = EkwingTaskListParser.studyCenterExamTasks(
            JSONObject(
                """
                {
                  "exam": [
                    {"type": "exam", "id": "exam-1"},
                    {"type": "exam", "id": "exam-2"}
                  ],
                  "groups": {
                    "homework": [
                      {"type": "hw", "id": "hw-1"},
                      {"type": "hw", "id": "hw-2"}
                    ],
                    "train": [
                      {"type": "train", "id": "train-1"}
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("exam-1", "exam-2"), tasks.map { it.optString("id") })
    }

    @Test
    fun homeworkListTasksKeepCurrentAndFinishedHomeworkItems() {
        val tasks = EkwingTaskListParser.homeworkListTasks(
            JSONObject(
                """
                {
                  "list": [
                    {"hid": "current-1", "title": "Current homework"},
                    {"hid": "finished-1", "title": "Finished homework", "type": "anything"}
                  ],
                  "page": {"currentPage": 1, "totalPage": 1}
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("current-1", "finished-1"), tasks.map { it.optString("hid") })
    }

    @Test
    fun answerAssemblerKeepsOtherHomeworksWhenOneFails() {
        val homeworks = listOf(
            JSONObject("""{"type":"hw","id":"ok-1","title":"可读取作业"}"""),
            JSONObject("""{"type":"hw","id":"fail-1","title":"失败作业"}"""),
        )
        val result = EkwingAnswerAssembler.buildAnswerState(homeworks) { homework ->
            if (homework.optString("id") == "fail-1") {
                error("详情接口失败")
            }
            listOf(
                EkwingAnswerSection(
                    title = "答案",
                    category = "answer",
                    originalText = "已读取",
                    questions = listOf(
                        EkwingAnswerQuestion(
                            order = "Q1",
                            question = "题目",
                            answer = "答案",
                        )
                    ),
                )
            )
        }

        assertEquals(listOf("可读取作业", "失败作业"), result.papers.map { it.title })
        val okSections = result.sectionsByPaperKey.getValue("hw:ok-1")
        val failedSections = result.sectionsByPaperKey.getValue("hw:fail-1")
        assertEquals("答案", okSections.single().title)
        assertEquals("读取失败", failedSections.single().title)
        assertEquals("error", failedSections.single().category)
        assertEquals("详情接口失败", failedSections.single().questions.single().answer)
    }

    @Test
    fun answerStateCanKeepExamListWithoutEagerSections() {
        val exam = JSONObject("""{"type":"exam","self_id":"exam-100","title":"当前考试"}""")
        val paper = exam.toAnswerPaperForTest(0)

        EkwingAnswerState.papers = listOf(paper)
        EkwingAnswerState.taskByPaperKey = mapOf(paper.key to exam.toString())
        EkwingAnswerState.sectionsByPaperKey = emptyMap()

        assertEquals(listOf("当前考试"), EkwingAnswerState.papers.map { it.title })
        assertEquals(exam.toString(), EkwingAnswerState.taskByPaperKey.getValue("exam:exam-100"))
        assertEquals(emptyMap<String, List<EkwingAnswerSection>>(), EkwingAnswerState.sectionsByPaperKey)
        EkwingAnswerState.clear()
    }

    @Test
    fun examStatusTextDisplaysKnownStatusCodes() {
        assertEquals("已完成", examStatusText("1"))
        assertEquals("未完成", examStatusText("2"))
        assertEquals("3", examStatusText("3"))
    }

    @Test
    fun answerStateClearRemovesLazyHomeworkCache() {
        EkwingAnswerState.papers = listOf(EkwingAnswerPaper("key", "title", "summary"))
        EkwingAnswerState.taskByPaperKey = mapOf("key" to "{}")
        EkwingAnswerState.sectionsByPaperKey = mapOf(
            "key" to listOf(
                EkwingAnswerSection(
                    title = "答案",
                    category = "answer",
                    originalText = "题目",
                    questions = emptyList(),
                )
            )
        )

        EkwingAnswerState.clear()

        assertEquals(emptyList<EkwingAnswerPaper>(), EkwingAnswerState.papers)
        assertEquals(emptyMap<String, String>(), EkwingAnswerState.taskByPaperKey)
        assertEquals(emptyMap<String, List<EkwingAnswerSection>>(), EkwingAnswerState.sectionsByPaperKey)
    }

    @Test
    fun reloginPlannerUsesSavedAccountCredentials() {
        val plan = EkwingReloginPlanner.plan(
            EkwingLoginIdentity(
                loginMethod = "account",
                username = " student001 ",
                name = null,
                schoolName = null,
                schoolId = null,
                password = "password123",
            )
        )

        assertEquals(EkwingReloginPlan.Account("student001", "password123"), plan)
    }

    @Test
    fun reloginPlannerUsesSavedRealNameCredentials() {
        val plan = EkwingReloginPlanner.plan(
            EkwingLoginIdentity(
                loginMethod = "real-name",
                username = null,
                name = " 张三 ",
                schoolName = " 第一中学 ",
                schoolId = " 1001 ",
                password = "password123",
            )
        )

        assertEquals(
            EkwingReloginPlan.RealName(
                name = "张三",
                password = "password123",
                schoolName = "第一中学",
                schoolId = "1001",
            ),
            plan,
        )
    }

    @Test
    fun reloginPlannerRequiresCompleteSavedCredentials() {
        val missingPassword = EkwingReloginPlanner.plan(
            EkwingLoginIdentity(
                loginMethod = "account",
                username = "student001",
                name = null,
                schoolName = null,
                schoolId = null,
                password = "",
            )
        )
        val missingSchool = EkwingReloginPlanner.plan(
            EkwingLoginIdentity(
                loginMethod = "real-name",
                username = null,
                name = "张三",
                schoolName = "第一中学",
                schoolId = "",
                password = "password123",
            )
        )

        assertEquals(EkwingReloginPlan.None, missingPassword)
        assertEquals(EkwingReloginPlan.None, missingSchool)
    }

    @Test
    fun reloginPlannerIgnoresUnknownLoginMethod() {
        val plan = EkwingReloginPlanner.plan(
            EkwingLoginIdentity(
                loginMethod = "unknown",
                username = "student001",
                name = "张三",
                schoolName = "第一中学",
                schoolId = "1001",
                password = "password123",
            )
        )

        assertEquals(EkwingReloginPlan.None, plan)
    }

    @Test
    fun sessionRefreshPlannerUsesRefreshedSessionWhenAvailable() {
        val current = EkwingLoginSession(
            uid = "old-uid",
            token = "old-token",
            userType = null,
        )
        val refreshed = EkwingLoginSession(
            uid = "new-uid",
            token = "new-token",
            userType = "student",
        )

        assertEquals(refreshed, EkwingSessionRefreshPlanner.actualSession(current, refreshed))
        assertEquals(current, EkwingSessionRefreshPlanner.actualSession(current, null))
    }

    @Test
    fun homeworkDetailParserAcceptsAlternateListKeysAndKeepsSameHidItems() {
        val items = EkwingHomeworkDetailParser.detailItems(
            JSONObject(
                """
                {
                  "data": {
                    "rows": [
                      {
                        "id": "item-1",
                        "hid": "homework-1",
                        "type_name": "朗读"
                      },
                      {
                        "id": "item-2",
                        "hid": "homework-1",
                        "type_name": "听选"
                      }
                    ]
                  },
                  "currentPage": 1,
                  "totalPage": 2
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("item-1", "item-2"), items.map { it.optString("id") })
    }

    @Test
    fun homeworkDetailParserAcceptsNestedDataContainer() {
        val items = EkwingHomeworkDetailParser.detailItems(
            JSONObject(
                """
                {
                  "data": {
                    "data": {
                      "list": [
                        {
                          "id": "nested-item",
                          "hid": "homework-1",
                          "tk_biz": "005"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("nested-item"), items.map { it.optString("id") })
    }

    @Test
    fun homeworkDetailParserReadsFlatPageFields() {
        val page = EkwingHomeworkDetailParser.pageInfo(
            JSONObject(
                """
                {
                  "items": [
                    {"id": "item-1", "hid": "homework-1"}
                  ],
                  "currentPage": 2,
                  "totalPage": 3
                }
                """.trimIndent()
            )
        )

        assertEquals(2, page?.optInt("currentPage"))
        assertEquals(3, page?.optInt("totalPage"))
    }

    @Test
    fun homeworkDetailParserNormalizesAlternatePageFields() {
        val page = EkwingHomeworkDetailParser.pageInfo(
            JSONObject(
                """
                {
                  "page": {
                    "pageNo": 4,
                    "pageCount": 6
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(4, page?.optInt("currentPage"))
        assertEquals(6, page?.optInt("totalPage"))
    }

    @Test
    fun homeworkDetailParserAcceptsNestedHwcidItemsWithoutId() {
        val items = EkwingHomeworkDetailParser.detailItems(
            JSONObject(
                """
                {
                  "data": {
                    "score": {
                      "rows": [
                        {
                          "hwcid": "course-300",
                          "hid": "item-hid-200",
                          "right_answer": "Answer from score detail"
                        }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("course-300"), items.map { it.optString("hwcid") })
    }

    @Test
    fun homeworkDetailItemKeyTreatsIdAndHwcidAsSameItem() {
        val fromItems = JSONObject(
            """
            {
              "id": "course-300",
              "hid": "item-hid-200",
              "url": "/student/Hw/item"
            }
            """.trimIndent()
        )
        val fromScoreDetail = JSONObject(
            """
            {
              "hwcid": "course-300",
              "hid": "item-hid-200",
              "right_answer": "Answer from score detail"
            }
            """.trimIndent()
        )

        assertEquals(
            EkwingHomeworkDetailParser.detailItemKey(fromItems),
            EkwingHomeworkDetailParser.detailItemKey(fromScoreDetail),
        )
    }

    @Test
    fun homeworkItemsRequestMatchesDemoPayload() {
        val request = EkwingHomeworkRequestPlanner.detailItemsRequest(
            homework = homeworkJson(),
            path = "/student/Hw/getHwItems",
            page = 2,
            commonParams = baseParams(),
        )

        assertEquals("/student/Hw/getHwItems", request.path)
        assertEquals("homework-100", request.payload["hid"])
        assertEquals("2", request.payload["page"])
        assertEquals("archive-9", request.payload["archiveId"])
        assertEquals("token-value", request.payload["token"])
        assertEquals(null, request.payload["self_id"])
        assertEquals(null, request.payload["is_exercise"])
    }

    @Test
    fun homeworkScoreDetailRequestAddsTrainingParams() {
        val request = EkwingHomeworkRequestPlanner.detailItemsRequest(
            homework = homeworkJson(),
            path = "/student/Hw/stuscoredetail",
            page = 1,
            commonParams = baseParams(),
        )

        assertEquals("/student/Hw/stuscoredetail", request.path)
        assertEquals("homework-100", request.payload["hid"])
        assertEquals("homework-100", request.payload["self_id"])
        assertEquals("0", request.payload["is_exercise"])
    }

    @Test
    fun homeworkDetailPathsTryItemsThenScoreDetail() {
        assertEquals(
            listOf(
                "/student/Hw/getHwItems",
                "/student/Hw/getBasicHwItems",
                "/student/Hw/stuscoredetail",
                "/student/Hw/stubasicscoredetail",
            ),
            EkwingHomeworkApiPlanner.detailPaths(useBasic = false),
        )
        assertEquals(
            listOf(
                "/student/Hw/getBasicHwItems",
                "/student/Hw/getHwItems",
                "/student/Hw/stubasicscoredetail",
                "/student/Hw/stuscoredetail",
            ),
            EkwingHomeworkApiPlanner.detailPaths(useBasic = true),
        )
    }

    @Test
    fun studyCenterPathsTryConfiguredAccountTypeThenFallback() {
        assertEquals(
            listOf("/student/Hw/getnewmainlist", "/student/Hw/getbasicnewmainlist"),
            EkwingHomeworkApiPlanner.studyCenterPaths(useBasic = false),
        )
        assertEquals(
            listOf("/student/Hw/getbasicnewmainlist", "/student/Hw/getnewmainlist"),
            EkwingHomeworkApiPlanner.studyCenterPaths(useBasic = true),
        )
    }

    @Test
    fun homeworkListPathsTryConfiguredAccountTypeThenFallback() {
        assertEquals(
            listOf("/student/Hw/getList", "/student/Hw/getBasicList"),
            EkwingHomeworkApiPlanner.homeworkListPaths(useBasic = false),
        )
        assertEquals(
            listOf("/student/Hw/getBasicList", "/student/Hw/getList"),
            EkwingHomeworkApiPlanner.homeworkListPaths(useBasic = true),
        )
    }

    @Test
    fun currentHomeworkListRequestMatchesDemoPayload() {
        val request = EkwingHomeworkRequestPlanner.homeworkListRequest(
            history = false,
            page = 1,
            commonParams = baseParams(),
        )

        assertEquals("new", request.payload["method"])
        assertEquals("1", request.payload["page"])
        assertEquals("desc", request.payload["sortMethod"])
        assertEquals("publish_times", request.payload["sortField"])
        assertEquals(null, request.payload["archiveId"])
        assertEquals("token-value", request.payload["token"])
    }

    @Test
    fun finishedHomeworkListRequestMatchesDemoPayload() {
        val request = EkwingHomeworkRequestPlanner.homeworkListRequest(
            history = true,
            page = 3,
            archiveId = "archive-last",
            commonParams = baseParams(),
        )

        assertEquals("finish", request.payload["method"])
        assertEquals("3", request.payload["page"])
        assertEquals("desc", request.payload["sortMethod"])
        assertEquals("finish_times", request.payload["sortField"])
        assertEquals("archive-last", request.payload["archiveId"])
    }

    @Test
    fun homeworkContentRequestMatchesDemoPayload() {
        val request = EkwingHomeworkRequestPlanner.contentRequest(
            homework = homeworkJson(),
            item = itemJson(),
            commonParams = baseParams(),
        )

        assertEquals("/student/Hw/hwdoitem", request.path)
        assertEquals("item-hid-200", request.payload["hid"])
        assertEquals("course-300", request.payload["hwcid"])
        assertEquals("archive-9", request.payload["archiveId"])
        assertEquals("last", request.payload["method"])
        assertEquals("0", request.payload["is_exercise"])
    }

    @Test
    fun homeworkContentRequestUsesAlternateItemIds() {
        val request = EkwingHomeworkRequestPlanner.contentRequest(
            homework = homeworkJson(),
            item = JSONObject(
                """
                {
                  "hw_id": "item-hw-201",
                  "hw_cnt_id": "course-301"
                }
                """.trimIndent()
            ),
            commonParams = baseParams(),
        )

        assertEquals("item-hw-201", request.payload["hid"])
        assertEquals("course-301", request.payload["hwcid"])
    }

    @Test
    fun homeworkContentRequestDoesNotUseRecordIdAsHwcid() {
        val itemWithoutHwcid = JSONObject(
            """
            {
              "hid": "item-hid-200",
              "unit_id": "unit-1",
              "type": "005",
              "record_id": "record-7"
            }
            """.trimIndent()
        )

        val contentError = assertThrows(RuntimeException::class.java) {
            EkwingHomeworkRequestPlanner.contentRequest(
                homework = homeworkJson(),
                item = itemWithoutHwcid,
                commonParams = baseParams(),
            )
        }
        assertEquals("作业小项缺少 hwcid/id", contentError.message)

        val answerRequests = EkwingHomeworkRequestPlanner.answerRequests(
            homework = homeworkJson(),
            item = itemWithoutHwcid,
            commonParams = baseParams(),
        )

        assertEquals(
            listOf(
                "/student/train/getitemans",
                "/student/train/getitemans",
                "/student/train/getjsitemans",
                "/student/train/getjsitemans",
            ),
            answerRequests.map { it.path },
        )
        assertEquals("record-7", answerRequests.last().payload["record_id"])
    }

    @Test
    fun homeworkRequestsUseIdsFromUrlsWhenFieldsAreMissing() {
        val request = EkwingHomeworkRequestPlanner.contentRequest(
            homework = JSONObject(
                """
                {
                  "url": "https://mapi.ekwing.com/student/Hw/start?hid=homework-from-url&archiveId=archive-from-url"
                }
                """.trimIndent()
            ),
            item = JSONObject(
                """
                {
                  "url": "https://mapi.ekwing.com/student/Hw/item?hid=item-hid-from-url&hwcid=course-from-url"
                }
                """.trimIndent()
            ),
            commonParams = baseParams(),
        )

        assertEquals("item-hid-from-url", request.payload["hid"])
        assertEquals("course-from-url", request.payload["hwcid"])
        assertEquals("archive-from-url", request.payload["archiveId"])
    }

    @Test
    fun homeworkAnswerRequestsMatchDemoPayloads() {
        val requests = EkwingHomeworkRequestPlanner.answerRequests(
            homework = homeworkJson(),
            item = itemJson(),
            commonParams = baseParams(),
        )

        assertEquals(
            listOf(
                "/student/Hw/getHwAns",
                "/student/Hw/getHwAns",
                "/student/Hw/gethwcnt",
                "/student/Hw/jshistoryitemScore",
                "/student/Hw/jshistoryitemScore",
                "/student/Hw/GetHwResult",
            ),
            requests.map { it.path },
        )

        val answerPayload = requests[0].payload
        assertEquals("LAST", answerPayload["method"])
        assertEquals(null, answerPayload["is_exercise"])

        val maxAnswerPayload = requests[1].payload
        assertEquals("MAX", maxAnswerPayload["method"])
        assertEquals(null, maxAnswerPayload["is_exercise"])

        val countPayload = requests[2].payload
        assertEquals("0", countPayload["is_exercise"])
        assertEquals(null, countPayload["method"])

        val historyPayload = requests[3].payload
        assertEquals("0", historyPayload["is_exercise"])
        assertEquals("last", historyPayload["method"])

        val historyWithoutMethodPayload = requests[4].payload
        assertEquals("0", historyWithoutMethodPayload["is_exercise"])
        assertEquals(null, historyWithoutMethodPayload["method"])

        val resultPayload = requests[5].payload
        assertEquals("0", resultPayload["is_exercise"])
        assertEquals(null, resultPayload["method"])

        requests.forEach { request ->
            assertEquals("item-hid-200", request.payload["hid"])
            assertEquals("course-300", request.payload["hwcid"])
            assertEquals("archive-9", request.payload["archiveId"])
            assertEquals("uid-value", request.payload["uid"])
        }
    }

    @Test
    fun homeworkAnswerRequestsIncludeTrainingFallbackWhenItemHasTrainingFields() {
        val requests = EkwingHomeworkRequestPlanner.answerRequests(
            homework = homeworkJson(),
            item = JSONObject(
                """
                {
                  "id": "course-300",
                  "hid": "item-hid-200",
                  "unit_id": "unit-1",
                  "type": "005",
                  "record_id": "record-7"
                }
                """.trimIndent()
            ),
            commonParams = baseParams(),
        )

        assertEquals(
            listOf(
                "/student/Hw/getHwAns",
                "/student/Hw/getHwAns",
                "/student/Hw/gethwcnt",
                "/student/Hw/jshistoryitemScore",
                "/student/Hw/jshistoryitemScore",
                "/student/Hw/GetHwResult",
                "/student/train/getitemans",
                "/student/train/getitemans",
                "/student/train/getjsitemans",
                "/student/train/getjsitemans",
            ),
            requests.map { it.path },
        )
        val trainingPayload = requests.last().payload
        assertEquals("unit-1", trainingPayload["unit_id"])
        assertEquals("005", trainingPayload["type"])
        assertEquals("record-7", trainingPayload["record_id"])
        assertEquals("archive-9", trainingPayload["archiveId"])
        assertEquals("MAX", trainingPayload["method"])
    }

    private fun parseHomeworkAnswers(value: Any): List<EkwingAnswerQuestion> {
        return EkwingAnswerParser.parseHomeworkAnswerQuestions(value)
    }

    private fun baseParams(): Map<String, String> {
        return mapOf(
            "uid" to "uid-value",
            "author_id" to "uid-value",
            "token" to "token-value",
        )
    }

    private fun homeworkJson(): JSONObject {
        return JSONObject(
            """
            {
              "hid": "homework-100",
              "archiveId": "archive-9"
            }
            """.trimIndent()
        )
    }

    private fun itemJson(): JSONObject {
        return JSONObject(
            """
            {
              "id": "course-300",
              "hid": "item-hid-200"
            }
            """.trimIndent()
        )
    }

    private fun JSONObject.toAnswerPaperForTest(index: Int): EkwingAnswerPaper {
        val title = optString("title").ifBlank { "考试 ${index + 1}" }
        val id = optString("self_id").ifBlank { optString("id") }
        return EkwingAnswerPaper(
            key = "${optString("type").ifBlank { "task" }}:$id",
            title = title,
            summary = "学习中心考试",
        )
    }

    private fun mojibake(value: String): String {
        return value.toByteArray(Charsets.UTF_8).toString(Charsets.ISO_8859_1)
    }
}
