# AI Service Placeholder Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the requested empty Python package and test placeholders under `ai-service` without changing the working uv configuration or Java project.

**Architecture:** This is a filesystem-only scaffold with no runtime behavior. Create only the requested zero-byte placeholders, then verify their existence, size, and the hashes of the protected uv metadata.

**Tech Stack:** Python 3.13 project layout, uv metadata, PowerShell verification

## Global Constraints

- Every newly created `.py` file must be exactly zero bytes.
- `ai-service/.env.example` must be exactly zero bytes.
- Preserve `ai-service/.python-version` with SHA-256 `02E735B3DFE1C32833EB550B7FF8FFA17F5F2BC3FA1E7BAE61A8F5A3883CE398`.
- Preserve `ai-service/pyproject.toml` with SHA-256 `8D27CEF2ADE49B948A08727EAE1D2342F99B23F644B6EB9DCFAA61AFBFA333F0`.
- Preserve `ai-service/uv.lock` with SHA-256 `8C6F46D4C9AA2D0157EFD9C7D76710B95EE4621D5B9D7A977C2A05CC5A61C7E2`.
- Do not modify the Java project.
- Do not add runtime behavior or files beyond the approved structure and required workflow records.

---

### Task 1: Create and verify the placeholder scaffold

**Files:**
- Create: `ai-service/app/__init__.py`
- Create: `ai-service/app/main.py`
- Create: `ai-service/app/api/__init__.py`
- Create: `ai-service/app/api/health.py`
- Create: `ai-service/app/core/__init__.py`
- Create: `ai-service/app/core/config.py`
- Create: `ai-service/app/schemas/__init__.py`
- Create: `ai-service/app/schemas/health.py`
- Create: `ai-service/tests/test_health.py`
- Create: `ai-service/.env.example`
- Preserve: `ai-service/.python-version`
- Preserve: `ai-service/pyproject.toml`
- Preserve: `ai-service/uv.lock`

**Interfaces:**
- Consumes: The existing `ai-service` directory and valid uv metadata.
- Produces: The exact requested package tree; all new files are intentionally empty and expose no runtime interfaces.

- [ ] **Step 1: Verify the preconditions**

Run from the repository root:

```powershell
$newFiles = @(
    '.\ai-service\app\__init__.py',
    '.\ai-service\app\main.py',
    '.\ai-service\app\api\__init__.py',
    '.\ai-service\app\api\health.py',
    '.\ai-service\app\core\__init__.py',
    '.\ai-service\app\core\config.py',
    '.\ai-service\app\schemas\__init__.py',
    '.\ai-service\app\schemas\health.py',
    '.\ai-service\tests\test_health.py',
    '.\ai-service\.env.example'
)
$existing = $newFiles | Where-Object { Test-Path -LiteralPath $_ }
if ($existing) { throw "Expected absent paths already exist: $($existing -join ', ')" }
```

Expected: exit code 0 with no output.

- [ ] **Step 2: Create the empty files**

Apply this exact patch from the repository root:

```diff
*** Begin Patch
*** Add File: ai-service/app/__init__.py
*** Add File: ai-service/app/main.py
*** Add File: ai-service/app/api/__init__.py
*** Add File: ai-service/app/api/health.py
*** Add File: ai-service/app/core/__init__.py
*** Add File: ai-service/app/core/config.py
*** Add File: ai-service/app/schemas/__init__.py
*** Add File: ai-service/app/schemas/health.py
*** Add File: ai-service/tests/test_health.py
*** Add File: ai-service/.env.example
*** End Patch
```

- [ ] **Step 3: Verify the scaffold and protected files**

Run from the repository root:

```powershell
$emptyFiles = @(
    '.\ai-service\app\__init__.py',
    '.\ai-service\app\main.py',
    '.\ai-service\app\api\__init__.py',
    '.\ai-service\app\api\health.py',
    '.\ai-service\app\core\__init__.py',
    '.\ai-service\app\core\config.py',
    '.\ai-service\app\schemas\__init__.py',
    '.\ai-service\app\schemas\health.py',
    '.\ai-service\tests\test_health.py',
    '.\ai-service\.env.example'
)
$missing = $emptyFiles | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) }
if ($missing) { throw "Missing files: $($missing -join ', ')" }
$nonempty = $emptyFiles | Where-Object { (Get-Item -LiteralPath $_).Length -ne 0 }
if ($nonempty) { throw "Expected zero-byte files: $($nonempty -join ', ')" }
$expectedHashes = @{
    '.\ai-service\.python-version' = '02E735B3DFE1C32833EB550B7FF8FFA17F5F2BC3FA1E7BAE61A8F5A3883CE398'
    '.\ai-service\pyproject.toml' = '8D27CEF2ADE49B948A08727EAE1D2342F99B23F644B6EB9DCFAA61AFBFA333F0'
    '.\ai-service\uv.lock' = '8C6F46D4C9AA2D0157EFD9C7D76710B95EE4621D5B9D7A977C2A05CC5A61C7E2'
}
foreach ($path in $expectedHashes.Keys) {
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
    if ($actual -ne $expectedHashes[$path]) { throw "Protected file changed: $path" }
}
Write-Output 'Placeholder scaffold verification passed.'
```

Expected: `Placeholder scaffold verification passed.` and exit code 0.

- [ ] **Step 4: Review the exact repository changes**

Run:

```powershell
git status --short
```

Expected: the new `ai-service` placeholders and this workflow plan are visible; no existing Java file is modified.

- [ ] **Step 5: Commit the scaffold**

```powershell
git add -- ai-service/app ai-service/tests ai-service/.env.example docs/superpowers/plans/2026-08-12-ai-service-placeholder-scaffold.md
git commit -m "chore: scaffold Python AI service"
```
