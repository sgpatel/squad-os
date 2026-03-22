# Publishing SquadOS to Maven Central

## One-time setup

### 1. Create a Sonatype account
- Go to https://issues.sonatype.org and create an account
- Open a new project ticket requesting `io.github.sgpatel` namespace
- Wait for approval (usually 1-2 business days)

### 2. Generate a GPG key
```bash
gpg --gen-key
# Use your real name and email
# Remember the passphrase

# List keys to find your key ID
gpg --list-secret-keys --keyid-format=long

# Export public key to keyserver
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Export private key (for GitHub Actions secret)
gpg --armor --export-secret-keys YOUR_KEY_ID
```

### 3. Add ~/.m2/settings.xml
```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>YOUR_SONATYPE_USERNAME</username>
      <password>YOUR_SONATYPE_TOKEN</password>
    </server>
  </servers>
</settings>
```

### 4. Add GitHub Actions secrets
In your GitHub repo → Settings → Secrets → Actions:
- `OSSRH_USERNAME` — your Sonatype username
- `OSSRH_TOKEN`    — your Sonatype user token (not password)
- `GPG_PRIVATE_KEY` — output of: gpg --armor --export-secret-keys YOUR_KEY_ID
- `GPG_PASSPHRASE`  — your GPG key passphrase

## Publishing a release

### Option A: Automatic (via git tag)
```bash
# 1. Update version in all poms
mvn versions:set -DnewVersion=1.2.0

# 2. Run tests
mvn clean test

# 3. Commit + tag
git add -A
git commit -m "release: v1.2.0"
git tag -a v1.2.0 -m "v1.2.0"
git push origin main --tags
# GitHub Actions automatically publishes to Maven Central
```

### Option B: Manual local publish
```bash
# Build, sign, and deploy squad-core only
mvn deploy -P release -pl squad-core -am -DskipTests
```

## Verify publication
After ~10 minutes, check:
- https://central.sonatype.com/artifact/io.github.sgpatel/squad-core
- https://search.maven.org/artifact/io.github.sgpatel/squad-core

## Usage after publication
```xml
<dependency>
  <groupId>io.github.sgpatel</groupId>
  <artifactId>squad-core</artifactId>
  <version>1.2.0</version>
</dependency>
```
