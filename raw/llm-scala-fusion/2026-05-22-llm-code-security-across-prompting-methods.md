# An Empirical Evaluation of LLM-Generated Code Security Across Prompting Methods

> Source: https://arxiv.org/abs/2605.24298
> Collected: 2026-09-19
> Published: 2026-05-22

Authors: Mohammed Kharma, Ahmed Sabbah, Mohammad Alkhanafseh, Mohammad Hammoudeh, David Mohaisen.

## Abstract

The growing use of Large Language Models (LLMs) for automated code generation has enhanced software development efficiency, but often at the cost of security. Generated code frequently overlooks critical concerns, leaving it vulnerable to issues such as weak encryption and improper input validation. To investigate this problem, we present a comprehensive empirical evaluation of the security quality of LLM-generated code across five LLMs and four programming languages (Java, C++, C, and Python), examining the impact of multiple prompt engineering methods. We introduce a weaknesses-aware zero-shot chain-of-thought (WA-0CoT) prompting strategy that enriches prompts with security context using CWE mappings to guide model reasoning. Our empirical analysis, supported by chi-square tests, finds no statistically significant reductions in vulnerability frequency or density across prompt methods. However, prompting strategies, including WA-0CoT, systematically influence the compositional distribution of CWE categories, with effects varying by programming language. These findings suggest that while security-aware prompting alters the structure of generated weaknesses, prompt engineering alone is insufficient to reliably reduce overall vulnerability levels. The results highlight the importance of language-aware and model-aware prompt design when evaluating the security properties of LLM-generated code.

## Related figures from the search-result summary (other studies in the same area)

- Multiple benchmark studies report that a substantial fraction (12–65%) of generated code snippets, depending on task, language, model, and prompting configuration, are non-compliant with basic secure coding standards or explicitly trigger CWE-classified vulnerabilities.
- A ~25–35% absolute gap exists between functional code and simultaneously secure, functional code across four major LLMs; for example, GPT-4o yields func@10 = 90.7%, func-sec@10 = 65.3% (CWEval).
- Prevalent weakness classes include buffer overflows (CWE-120/787), unchecked return values (CWE-252/253), hard-coded credentials (CWE-259/798), SQL injection (CWE-89), code injection (CWE-78), cryptographic misuse (CWE-780), CWE-532 (Insertion of Sensitive Information in Log File), CWE-321 (Use of Hard-coded Cryptographic Key).
- A 2025 study of Copilot-generated code in GitHub projects: approximately 27.3% of generated code contained security weaknesses, spanning 43 CWE categories.
