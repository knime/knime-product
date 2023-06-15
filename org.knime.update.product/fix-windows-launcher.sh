cd target/repository
zip -9j binary/org.knime.desktop.product.executable.win32.win32.x86_64_* "../../win32/x86_64/knime.exe"
unzip artifacts.jar

SIZE=$(stat -c %s binary/org.knime.desktop.product.executable.win32.win32.x86_64_*)
MD5=$(md5sum binary/org.knime.desktop.product.executable.win32.win32.x86_64_* | awk '{ print $1 }')
SHA=$(sha256sum binary/org.knime.desktop.product.executable.win32.win32.x86_64_* | awk '{ print $1 }')
SHA_512=$(sha512sum binary/org.knime.desktop.product.executable.win32.win32.x86_64_* | awk '{ print $1 }')

xmlstarlet ed -L \
    -u "/repository/artifacts/artifact[@id='org.knime.desktop.product.executable.win32.win32.x86_64']/properties/property[@name='download.size']/@value" -v $SIZE \
    -u "/repository/artifacts/artifact[@id='org.knime.desktop.product.executable.win32.win32.x86_64']/properties/property[@name='artifact.size']/@value" -v $SIZE \
    -u "/repository/artifacts/artifact[@id='org.knime.desktop.product.executable.win32.win32.x86_64']/properties/property[@name='download.md5']/@value" -v $MD5 \
    -u "/repository/artifacts/artifact[@id='org.knime.desktop.product.executable.win32.win32.x86_64']/properties/property[@name='download.checksum.md5']/@value" -v $MD5 \
    -u "/repository/artifacts/artifact[@id='org.knime.desktop.product.executable.win32.win32.x86_64']/properties/property[@name='download.checksum.sha-256']/@value" -v $SHA \
    -u "/repository/artifacts/artifact[@id='org.knime.desktop.product.executable.win32.win32.x86_64']/properties/property[@name='download.checksum.sha-512']/@value" -v $SHA_512 \
    artifacts.xml

zip -9 artifacts.jar artifacts.xml
xz -9f artifacts.xml