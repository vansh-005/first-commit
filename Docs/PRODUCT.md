A multimodal personal memory layer that lets users upload their digital clutter—documents, PDFs, images, screenshots, audio and video—and later retrieve the right information using natural language without needing to remember filenames or folder locations.

- **Problem:** People accumulate screenshots, PDFs, documents, receipts, lecture notes, audio, videos, etc. across devices/folders and later cannot remember where something was stored or what its filename was.
- **Product:** A multimodal personal memory layer where users can upload files individually or in batches and later retrieve them using natural-language memory cues.
- **Core inputs:** images, PDFs, documents, audio, video. We can support the widest practical set during the hackathon, but the product spec shouldn't promise perfect parsing of every obscure format.
- **Core experience:** upload → system understands/indexes → uploads remain browsable → ask/query later → retrieve matching files/content → show why each result matched and provide source references.
- **Important distinction:** this is not just “chat with a PDF.” Search happens **across the user's entire personal corpus** and across modalities.
- **MVP:** authentication, batch upload, persistent file library, processing status, semantic search across uploaded data, result previews/source references, and optionally an answer synthesized from retrieved content.
- **Stretch:** proactive “memory intelligence” such as dates, expiry/renewal detection, auto-generated categories/tags, related-memory discovery, duplicate detection, and notifications.
- **Non-goal for MVP:** building Dropbox/Google Drive replacement features such as complex sharing, collaborative editing, version history, permissions hierarchies, or file synchronization.
