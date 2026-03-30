source 'https://mirrors.aliyun.com/rubygems/'

# You may use http://rbenv.org/ or https://rvm.io/ to install and use this version
ruby ">= 2.6.10"

# Exclude problematic versions of cocoapods and activesupport that causes build failures.
gem 'cocoapods', '~> 1.16.2'
gem 'activesupport', '~> 6.1.7'
gem 'xcodeproj', '~> 1.27.0'
gem 'concurrent-ruby', '< 1.3.4'

# Ruby标准库（某些版本可能已从标准库移除）
gem 'bigdecimal'
gem 'logger'
gem 'benchmark'
gem 'mutex_m'
gem 'base64'  # Required for Ruby 4.0+ where base64 was removed from default gems
gem 'tsort'    # Required for Ruby 4.1+ where tsort was removed from default gems
gem 'nkf'      # Required for Ruby 3.4+ where nkf was removed from default gems

# Use minitest 6.x for Ruby 4.0.1 compatibility (supports Ruby >= 3.2)
gem 'minitest', '~> 6.0'

# drb 2.2.3+ requires Ruby >= 2.7.0
gem 'drb', '2.0.6'