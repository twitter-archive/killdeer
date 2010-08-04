require 'capistrano/recipes/deploy/strategy/copy'
require 'fileutils'
require 'tempfile'

module Capistrano
  module Deploy
    module Strategy
      class Build < Copy
        def deploy!
          if copy_cache
            if File.exists?(copy_cache)
              logger.debug "refreshing local cache to revision #{revision} at #{copy_cache}"
              system(source.sync(revision, copy_cache))
            else
              logger.debug "preparing local cache at #{copy_cache}"
              system(source.checkout(revision, copy_cache))
            end

            logger.debug "copying cache to deployment staging area #{destination}"
            Dir.chdir(copy_cache) do
              FileUtils.mkdir_p(destination)
              queue = Dir.glob("*", File::FNM_DOTMATCH)
              while queue.any?
                item = queue.shift
                name = File.basename(item)

                next if name == "." || name == ".."
                next if copy_exclude.any? { |pattern| File.fnmatch(pattern, item) }

                if File.symlink?(item)
                  FileUtils.ln_s(File.readlink(File.join(copy_cache, item)), File.join(destination, item))
                elsif File.directory?(item)
                  queue += Dir.glob("#{item}/*", File::FNM_DOTMATCH)
                  FileUtils.mkdir(File.join(destination, item))
                else
                  FileUtils.ln(File.join(copy_cache, item), File.join(destination, item))
                end
              end
            end
          else
            logger.debug "getting (via #{copy_strategy}) revision #{revision} to #{destination}"
            system(command)

            if copy_exclude.any?
              logger.debug "processing exclusions..."
              copy_exclude.each { |pattern| FileUtils.rm_rf(Dir.glob(File.join(destination, pattern), File::FNM_DOTMATCH)) }
            end
          end

          raise unless system("cd #{source_folder} && #{build_task}")
          remote_filename = File.join(releases_path, package)

          begin
            upload(filename, remote_filename, :via => :scp)
            run "cd #{release_path} && #{decompress(remote_filename).join(" ")}"
          ensure
            run "rm -f #{remote_filename}"
          end

        ensure
          FileUtils.rm filename rescue nil
          FileUtils.rm_rf destination rescue nil
        end

        def build_task
          @build_task ||= configuration[:build_task]
        end

        def copy_compression
          configuration[:copy_compression]
        end

        def dist_path
          configuration[:dist_path]
        end

        def filename
          @filename ||= File.join(tmpdir, File.basename(destination), dist_path, package)
        end

        def package
          configuration[:package_name] || "#{application}-#{revision[0, 8]}.#{copy_compression}"
        end

        def source_folder
          File.join(tmpdir, File.basename(destination))
        end
      end
    end
  end
end
