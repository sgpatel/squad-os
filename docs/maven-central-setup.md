# Publishing SquadOS to Maven Central

## How releases work

Releases are fully automated. Push a git tag to trigger the pipeline:

```bash
# 1. Bump versions in all poms
# (replace 2.1.0 with new version)
find . -name "pom.xml" -exec sed -i '' 's/2.1.0/2.2.0/g' {} +

# 2. Run tests
mvn clean test

# 3. Commit + tag
git add -A
git commit -m "release: v2.2.0"
git tag -a v2.2.0 -m "v2.2.0 — description"
git push origin master --tags
```

GitHub Actions then:
1. Runs all 170 tests
2. Publishes squad-core to Maven Central
3. Creates GitHub Release with dependency snippet

## One-time setup (already done)

### Sonatype account
- Namespace `io.github.sgpatel` verified at central.sonatype.com

### GPG key
```bash
gpg --gen-key
gpg --keyserver hkps://keys.openpgp.org --send-keys YOUR_KEY_ID
gpg --armor --export-secret-keys YOUR_KEY_ID > gpg-private-key.asc
```

### GitHub secrets (already configured)
| Secret | Value |
|--------|-------|
| OSSRH_USERNAME | Sonatype token username |
| OSSRH_TOKEN | Sonatype token password |
| GPG_PRIVATE_KEY | Contents of gpg-private-key.asc |
| GPG_PASSPHRASE | GPG key passphrase |

## Published versions

| Version | Features | Maven Central |
|---------|----------|---------------|
| 1.2.0 | Core + parallel + pgvector | ✓ live |
| 2.0.0 | + Multi-node Redis Pub/Sub | ✓ live |
| 2.1.0 | + @SquadPlan typed output | ✓ live |

## Usage after publication

```xml
<dependency>
  <groupId>io.github.sgpatel</groupId>
  <artifactId>squad-core</artifactId>
  <version>2.1.0</version>
</dependency>
```
