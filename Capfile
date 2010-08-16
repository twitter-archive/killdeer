# regular deploys are as easy as "cap deploy"
# this will do a git clone of the code, build a package, sftp the package to the machine.

# Command line arguments:
#
#   key=/path/to/ec2.pem (default: ~/.ec2.pem)
#   host=ec2-123-example-amazon.com (no default, must specify)

require 'deploy/strategy/build'

BEGIN {
  KEY = ENV['key'] || "#{ENV['HOME']}/.ec2.pem"
  system "ssh-add '#{KEY}'"
}

load 'deploy'

set :ssh_options, {:auth_methods => %w{ publickey }, :keys => [KEY] }

set :user, ENV['user'] || 'root'
set :application, File.basename(`git config --get remote.origin.url`.chomp, '.*')

# Remote paths
set :deploy_to, "/tmp/#{application}"
set :currentloc, "#{deploy_to}/current"
set :logloc, "#{deploy_to}/log"
set :log, "#{logloc}/#{application}.log"

set :repository, "git@github.com:twitter/#{application}.git"
set :branch, "master"
set :scm, :git
set :strategy, Capistrano::Deploy::Strategy::Build.new(self)
set :copy_cache, true

set :remote_unzip_dir, release_name
set :keep_releases, 3
set :build_task, "sbt clean update package-dist"
set :deploy_via, :copy
set :dist_path, "dist"

version = `git rev-parse HEAD`[0..7]
set :version, version

set :copy_compression, :zip
set :package_name, "#{application}-#{version}.#{copy_compression}"

hosts = [ENV['host'] || abort("You must specify a host with host=yourhost.com")]

role :app, *hosts

namespace :deploy do
 [:finalize_update, :restart].each do |default_task|
   task default_task do
     # nothing
   end
 end

  before 'deploy', 'deploy:setup'
  after 'deploy', 'deploy:restart'

  desc "Directory setup of remote machines."
  task :setup, :roles => :app do
    run "mkdir -p #{deploy_to}"
    run "mkdir -p #{deploy_to}/releases"
    run "mkdir -p #{logloc}"
  end

  desc "Start"
  task :start, :roles => :app do
    run "cd #{currentloc}; nohup bash #{currentloc}/scripts/#{application}.sh > #{log} &"
  end

  desc "Stop"
  task :stop, :roles => :app do
    run "pkill -f #{application} &>/dev/null"
  end

  desc "Restart"
  task :restart do
    stop
    start
  end

  task :update_code, :roles => :app do
    on_rollback { run "rm -rf #{release_path}; true" }
    run "mkdir -p #{release_path}"
    strategy.deploy!
    finalize_update
  end
end
