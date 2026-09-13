import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain

def credentialId = 'school-linux-agent'
def keyPath = '/var/jenkins_home/.ssh/school_agent_ed25519'
def store = SystemCredentialsProvider.getInstance().getStore()
def domain = Domain.global()
def existing = store.getCredentials(domain).find { it.id == credentialId }

if (new File(keyPath).isFile() && existing == null) {
    def keySource = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(
        new File(keyPath).getText('UTF-8'))
    def credential = new BasicSSHUserPrivateKey(
        CredentialsScope.GLOBAL, credentialId, 'jenkins', keySource, '',
        'Dedicated key for the school-linux Jenkins build agent')
    store.addCredentials(domain, credential)
}
