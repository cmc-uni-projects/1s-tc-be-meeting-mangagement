pipeline {
    agent any

    parameters {
        string(name: 'BUILD_TAG', defaultValue: 'v1.0.0', description: 'Version Tag (e.g., v1.0.0)')
        choice(name: 'TARGET_SERVICE', choices: ['meeting-management-backend'], description: 'Service Name')
    }


    environment {
        // --- CẤU HÌNH DOCKER ---
        DOCKER_REGISTRY_USER = "gk123a"
        IMAGE_NAME = "meeting-management-backend"
        DOCKER_CREDENTIALS_ID = "docker-hub-credentials"
       
        // --- CẤU HÌNH GIT ---
        // Lưu ý: Link repo của bạn có chữ "Mangagement" (có thể do typo lúc tạo repo, tôi giữ nguyên theo link bạn gửi)
        GIT_REPO_RAW_URL = "github.com/gk12355a/Meeting-Mangagement.git"
        GIT_REPO_FULL_URL = "https://github.com/gk12355a/Meeting-Mangagement.git"
        GIT_CREDENTIALS_ID = "github-https-cred-ids" 
        
        // --- CẤU HÌNH MÔI TRƯỜNG ---
        BRANCH = "${env.GIT_BRANCH}".replaceFirst(/^origin\//, '')
        NAMESPACE = "${BRANCH == 'k8s' ? 'prod' : 'dev'}"
        
        // --- ĐƯỜNG DẪN (QUAN TRỌNG) ---
        // Tên thư mục chứa mã nguồn backend trong repo
        PROJECT_ROOT = "management-meeting-backend" 
        // Đường dẫn file manifest tính từ root của repo
        YAML_DIR = "${PROJECT_ROOT}/manifest"    
    }

    stages {
        stage('Approval') {
            steps {
                script {
                    input message: "Deploy Backend ${params.BUILD_TAG} to [${NAMESPACE}]?", ok: "Yes, Deploy"
                }
            }
        }

        stage('Checkout Code') {
            steps {
                git branch: "${BRANCH}", 
                    credentialsId: "${GIT_CREDENTIALS_ID}", 
                    url: "${GIT_REPO_FULL_URL}"
            }
        }

        stage('Build & Push Docker Image') {
            steps {
                script {
                    // Di chuyển vào thư mục chứa mã nguồn backend
                    dir("${PROJECT_ROOT}") {
                        docker.withRegistry('', "${DOCKER_CREDENTIALS_ID}") {
                            def fullImageName = "${DOCKER_REGISTRY_USER}/${IMAGE_NAME}:${params.BUILD_TAG}-${NAMESPACE}"
                            
                            echo "Building Docker Image using DockerfileBe..."
                            // QUAN TRỌNG: Chỉ định tên file là DockerfileBe bằng tham số -f
                            def image = docker.build(fullImageName, "-f DockerfileBe .")
                            
                            echo "Pushing image to Docker Hub..."
                            image.push()
                            image.push("latest") 
                        }
                    }
                }
            }
        }

        stage('GitOps: Update Manifest') {
            steps {
                script {
                    // Script nằm trong thư mục backend, cần cấp quyền và chạy
                    dir("${PROJECT_ROOT}") {
                        // Tôi giả định bạn sẽ đặt file script này vào trong folder management-meeting-backend
                        sh "chmod +x update_images_scripts.sh"
                        
                        // Lưu ý: Đường dẫn YAML_DIR truyền vào script giờ là "manifest" (tương đối so với thư mục hiện tại)
                        sh "./update_images_scripts.sh ${IMAGE_NAME} ${params.BUILD_TAG} ${NAMESPACE} manifest"
                    }
                }
            }
        }

        stage('GitOps: Commit & Push') {
            environment {
                TARGET_BRANCH = "${BRANCH}"
                TARGET_YAML_DIR = "${YAML_DIR}"
                NEW_TAG = "${params.BUILD_TAG}"
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: "${GIT_CREDENTIALS_ID}", 
                        usernameVariable: 'GIT_USER', 
                        passwordVariable: 'GIT_PASS'
                    )
                ]) {
                    sh '''
                        set -e
                        
                        echo "--- 1. Cấu hình Git User ---"
                        git config user.name "jenkins-bot"
                        git config user.email "jenkins@ci.com"

                        echo "--- 2. Staging Files ---"
                        # Chỉ add các file YAML
                        git add $TARGET_YAML_DIR/*.yaml

                        echo "--- 3. Committing ---"
                        # Sử dụng git diff --cached --quiet
                        # Lệnh này chỉ kiểm tra sự thay đổi trong các file ĐÃ ADD (Staging Area)
                        # Nếu file YAML không đổi -> trả về True (Quiet) -> Nhảy xuống else
                        # Nếu file YAML có đổi -> trả về False -> Nhảy vào then để commit
                        
                        if ! git diff --cached --quiet; then
                            git commit -m "GitOps: Update Backend image to $NEW_TAG [ci skip]"
                            echo "Changes committed successfully."
                        else
                            echo "No changes in manifest to commit. Skipping..."
                        fi

                        echo "--- 4. Cleaning Workspace ---"
                        git reset --hard HEAD
                        git clean -fd

                        echo "--- 5. Pulling & Rebasing ---"
                        git pull origin $TARGET_BRANCH --rebase

                        echo "--- 6. Pushing ---"
                        # Chỉ push khi có commit mới (nhưng chạy lệnh push thừa cũng không sao, git sẽ báo Everything up-to-date)
                        git push https://$GIT_USER:$GIT_PASS@$GIT_REPO_RAW_URL $TARGET_BRANCH
                    '''
                }
            }
        }
    }
}