import copy
import json
import unittest
from pathlib import Path
from import_catalog import build

class CatalogTests(unittest.TestCase):
    def setUp(self):
        self.video = dict(vid='abcdefghijk',title='A talk',channel='Speaker',summary='Summary',why_hn='Why',score=5,shelf_life='evergreen',categories=[dict(area_name='Computing')])
        self.mentions = [dict(vid='abcdefghijk',stance='rec',month=month,author=str(i),hn_comment_id=i,quote='Good talk',why='Good') for i,month in enumerate(['2020-01','2020-06','2022-01'])]
    def test_repeated_over_years(self):
        self.assertEqual(len(build([self.video],self.mentions)),1)
    def test_neutral_mentions_do_not_count(self):
        self.mentions[0]['stance']='reference'
        self.assertEqual(build([self.video],self.mentions),[])
    def test_same_author_does_not_count(self):
        for m in self.mentions:m['author']='same'
        self.assertEqual(build([self.video],self.mentions),[])
    def test_same_year_does_not_count(self):
        self.mentions[2]['month']='2020-10'
        self.assertEqual(build([self.video],self.mentions),[])
    def test_same_month_does_not_count(self):
        self.mentions[1]['month']='2020-01'
        self.assertEqual(build([self.video],self.mentions),[])
    def test_news_excluded(self):
        self.video['shelf_life']='news'
        self.assertEqual(build([self.video],self.mentions),[])
    def test_strong_recommendation_counts(self):
        self.mentions[0]['stance']='strong_rec'
        self.assertEqual(len(build([self.video],self.mentions)),1)
    def test_bundled_catalog_invariants(self):
        catalog=json.loads(Path('app/src/main/assets/catalog.json').read_text())
        self.assertGreater(len(catalog),2000)
        self.assertEqual(len(catalog),len({v['id'] for v in catalog}))
        self.assertEqual([v['score'] for v in catalog],sorted((v['score'] for v in catalog),reverse=True))
        for v in catalog:
            self.assertRegex(v['id'],r'^[A-Za-z0-9_-]{11}$')
            self.assertGreaterEqual(len({e['author'] for e in v['evidence']}),3)
            self.assertGreaterEqual(len({e['month'] for e in v['evidence']}),3)
            self.assertGreaterEqual(len({e['month'][:4] for e in v['evidence']}),2)

if __name__=='__main__':unittest.main()
