import copy
import json
from pathlib import Path
import unittest

import evaluate


ROOT = Path(__file__).parent
LEGACY = json.loads((ROOT / 'evaluation.json').read_text())
EXPANDED = json.loads((ROOT / 'evaluation-multi-document.json').read_text())


class EvaluationTest(unittest.TestCase):
    def test_expansion_preserves_entire_original_corpus_and_all_thirty_questions(self):
        self.assertEqual(LEGACY['documents'], EXPANDED['documents'])
        self.assertEqual(LEGACY['spaces'], EXPANDED['spaces'])
        self.assertEqual(LEGACY['questions'], EXPANDED['questions'][:30])
        self.assertEqual(40, len(EXPANDED['questions']))
        evaluate.validate_dataset(LEGACY)
        evaluate.validate_dataset(EXPANDED)

    def test_all_expected_sources_are_required_not_just_one(self):
        question = EXPANDED['questions'][30]
        self.assertEqual(['backup', 'logs'], evaluate.expected_documents(question))
        text = 'Резервные копии: 14 суток. Журналы: 7 суток.'
        partial = evaluate.answer_coverage(question, [1, 2], {1}, text, False)
        self.assertFalse(partial['expectedSourceCited'])
        self.assertFalse(partial['multiSourceCompleteAnswer'])
        self.assertEqual([2], partial['missingExpectedSourceIds'])
        self.assertTrue(evaluate.answer_coverage(question, [1, 2], {1, 2}, text, False)['multiSourceCompleteAnswer'])

    def test_citations_without_required_fact_or_explicit_gap_are_incomplete(self):
        question = EXPANDED['questions'][30]
        answer = evaluate.answer_coverage(question, [1, 2], {1, 2}, 'Резервные копии: 14 суток. Журналы.', False)
        self.assertFalse(answer['multiSourceCompleteAnswer'])
        partial = {'answerable': True, 'evidenceKeywordGroups': {'one': ['02:30'], 'two': ['restore-sandbox']},
                   'expectedGapKeywordGroups': ['неизвестна', 'длительность']}
        supported = 'Копия в 02:30, проверка в restore-sandbox.'
        self.assertFalse(evaluate.answer_coverage(partial, [1, 2], {1, 2}, supported, False)['multiSourceCompleteAnswer'])
        self.assertTrue(evaluate.answer_coverage(partial, [1, 2], {1, 2}, supported + ' Длительность неизвестна.', False)['multiSourceCompleteAnswer'])
        self.assertFalse(evaluate.answer_coverage(partial, [1, 2], {1, 2}, supported + ' Длительность неизвестна.', True)['multiSourceCompleteAnswer'])

    def test_multi_source_gate_cannot_hide_behind_many_passing_single_source_cases(self):
        cases = [self.good_case([1]) for _ in range(24)] + [self.good_case([1, 2]) for _ in range(10)]
        for case in cases[-2:]:
            case['multiSourceCompleteAnswer'] = False
        summary = evaluate.summarize(cases, EXPANDED['gates'])
        self.assertEqual(.8, summary['multiSourceCompleteAnswerRate'])
        self.assertFalse(summary['checks']['multiSourceCompleteAnswer'])
        self.assertFalse(summary['passed'])
        cases[-1]['multiSourceCompleteAnswer'] = True
        self.assertTrue(evaluate.summarize(cases, EXPANDED['gates'])['passed'])

    def test_legacy_recall_at_five_is_not_replaced_by_recall_at_eight(self):
        cases = [self.good_case([1]) for _ in range(24)]
        for case in cases[:3]:
            case['expectedInTop5'] = False
        summary = evaluate.summarize(cases, EXPANDED['gates'])
        self.assertTrue(summary['checks']['answerableRecallAtK'])
        self.assertFalse(summary['checks']['legacySingleSourceRecallAt5'])
        self.assertFalse(summary['passed'])

    def test_multi_source_success_cannot_hide_three_old_false_abstentions(self):
        cases = [self.good_case([1]) for _ in range(24)] + [self.good_case([1, 2]) for _ in range(10)]
        for case in cases[:3]:
            case.update(insufficientEvidence=True, sourceCount=0, expectedSourceCited=False)
        summary = evaluate.summarize(cases, EXPANDED['gates'])
        self.assertTrue(summary['checks']['answerableGroundedResponse'])
        self.assertTrue(summary['checks']['answerableExpectedSourceCited'])
        self.assertTrue(summary['checks']['multiSourceCompleteAnswer'])
        self.assertFalse(summary['checks']['singleSourceGroundedResponse'])
        self.assertFalse(summary['checks']['singleSourceExpectedSourceCited'])
        self.assertEqual(21, summary['singleSourceGroundedResponseCount'])
        self.assertEqual(21, summary['singleSourceExpectedSourceCitedCount'])
        self.assertFalse(summary['passed'])

    def test_invalid_expected_sets_and_missing_evidence_labels_are_rejected(self):
        for expected in (['backup', 'backup'], ['backup', 'missing'], []):
            data = copy.deepcopy(EXPANDED)
            data['questions'][30]['expectedDocuments'] = expected
            with self.assertRaises(evaluate.EvaluationError):
                evaluate.validate_dataset(data)
        data = copy.deepcopy(EXPANDED)
        del data['questions'][30]['evidenceKeywordGroups']['logs']
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.validate_dataset(data)

    @staticmethod
    def good_case(expected):
        return {'answerable': True, 'expectedDocumentIds': expected, 'expectedInTopK': True,
                'expectedInTop5': True, 'expectedSourceCited': True, 'insufficientEvidence': False,
                'sourceCount': len(expected), 'httpStatus': 200, 'citationProblems': [],
                'unexpectedDocumentIds': [], 'multiSourceCompleteAnswer': True}


if __name__ == '__main__':
    unittest.main()
