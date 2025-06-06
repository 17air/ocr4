# OCR4

This project provides a simple Streamlit app that recognizes Korean business cards using an OCR API and extracts key information using a NER API.

## Requirements

- Python 3.8+
- [Streamlit](https://streamlit.io/) and `requests` library.
- API keys:
  - `OCR_API_KEY` for the [ocr.space](https://ocr.space/ocrapi) OCR service.
  - `HF_API_TOKEN` for the Hugging Face inference API.

Install dependencies with:

```bash
pip install streamlit requests
```

## Running

Set the required environment variables and run the Streamlit app:

```bash
export OCR_API_KEY=your_ocr_api_key
export HF_API_TOKEN=your_huggingface_token
streamlit run app.py
```

The web interface lets you capture an image or choose one from the gallery. It displays the OCR result and the extracted fields (이름, 전화번호, 메일, 직책, 회사).
