# Data attribution

This app contains a filtered derivative of **HackerTube**, https://hackertube.org/, public export dated 2026-10-31 (6,454 video records and 39,834 labeled mentions). The resulting catalog contains 2,074 videos.

Source data is released under the **Open Data Commons Attribution License (ODC-By) v1.0**:
https://opendatacommons.org/licenses/by/1-0/

Credit also belongs to **open-index/hacker-news**:
https://huggingface.co/datasets/open-index/hacker-news

Hacker News data is provided by Y Combinator. Comments remain the work of their authors. Video titles, channel names, and thumbnails belong to their creators. This app is not affiliated with Karpathy, HackerTube's original author, YouTube, Google, Hacker News or Y Combinator.

Changes: retained only actual recommendation labels with at least three distinct authors, three months and two calendar years; excluded news; projected fields for a TV catalog and sorted by source score. Summaries and stance labels originate from the source's AI pipeline and are not independently verified.

Original source documentation is preserved in docs/DATA.md. The bundled derived dataset (app/src/main/assets/catalog.json) and upstream data documentation retain their upstream terms; they are not relicensed under MIT.

The repository's original application code and original project documentation are licensed under the MIT License in LICENSE. Third-party components retain their own licenses, including the Apache-2.0-licensed Gradle wrapper. Video imagery visible in screenshots, thumbnails, video content, names and trademarks remain subject to their respective owners' rights; the MIT license does not grant rights to that third-party material.
