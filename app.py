import os
import re
import requests
import streamlit as st

OCR_API_KEY = os.getenv("OCR_API_KEY")
HF_API_TOKEN = os.getenv("HF_API_TOKEN")

OCR_URL = "https://api.ocr.space/parse/image"
NER_URL = "https://api-inference.huggingface.co/models/soddokayo/klue-roberta-base-ner"

HEADERS_NER = {"Authorization": f"Bearer {HF_API_TOKEN}"} if HF_API_TOKEN else {}

PHONE_REGEX = re.compile(r"(?:\+82[- ]?1\d{1}|0\d{1,2})[- ]?\d{3,4}[- ]?\d{4}")
EMAIL_REGEX = re.compile(r"[\w.]+@[\w.-]+\.[A-Za-z]{2,6}")

st.title("명함 OCR 및 NER")

uploaded_file = st.file_uploader("갤러리 선택", type=["png", "jpg", "jpeg"])
camera_file = st.camera_input("카메라찍기")

image_file = camera_file or uploaded_file
if image_file is not None:
    image_bytes = image_file.getvalue()
    st.image(image_bytes, caption="입력 이미지", use_column_width=True)

    if not OCR_API_KEY:
        st.error("환경 변수 OCR_API_KEY가 설정되지 않았습니다.")
        st.stop()

    with st.spinner("OCR 처리 중..."):
        resp = requests.post(
            OCR_URL,
            headers={"apikey": OCR_API_KEY},
            files={"file": image_bytes},
            data={"language": "kor", "isOverlayRequired": False},
        )
        resp.raise_for_status()
        result = resp.json()
        if result.get("IsErroredOnProcessing"):
            st.error("OCR 처리 오류: " + str(result.get("ErrorMessage")))
            st.stop()
        text = result.get("ParsedResults", [{}])[0].get("ParsedText", "")

    st.subheader("OCR 결과")
    st.text_area("텍스트", text, height=200)

    info = {"이름": "", "전화번호": "", "메일": "", "직책": "", "회사": ""}
    if HF_API_TOKEN:
        with st.spinner("NER 처리 중..."):
            ner_resp = requests.post(NER_URL, headers=HEADERS_NER, json={"inputs": text})
            if ner_resp.status_code == 401:
                st.warning("HF_API_TOKEN이 올바르지 않습니다.")
            else:
                ner_resp.raise_for_status()
                ner_data = ner_resp.json()
                entities = {}
                for item in ner_data:
                    label = item.get("entity_group") or item.get("entity")
                    word = item.get("word", "")
                    if word.startswith("##"):
                        word = word[2:]
                    if label not in entities:
                        entities[label] = word
                    else:
                        if not word.startswith(" ") and not entities[label].endswith(" "):
                            entities[label] += " "
                        entities[label] += word
                info["이름"] = entities.get("PS", "")
                info["직책"] = entities.get("TI", "")
                info["회사"] = entities.get("OG", "")
    else:
        st.info("HF_API_TOKEN이 설정되지 않아 NER을 수행하지 않습니다.")

    phone_match = PHONE_REGEX.search(text)
    if phone_match:
        info["전화번호"] = phone_match.group()
    email_match = EMAIL_REGEX.search(text)
    if email_match:
        info["메일"] = email_match.group()

    st.subheader("분류된 정보")
    for k, v in info.items():
        st.write(f"**{k}**: {v}")
